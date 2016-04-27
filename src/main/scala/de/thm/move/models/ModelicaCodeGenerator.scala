/**
 * Copyright (C) 2016 Nicola Justus <nicola.justus@mni.thm.de>
 */

package de.thm.move.models

import java.io.PrintWriter
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.{Paths, Files}
import java.util.Base64
import javafx.scene.Node
import javafx.scene.paint.{Paint, Color}
import javafx.scene.shape.{LineTo, MoveTo}
import javafx.scene.text.TextAlignment

import de.thm.move.models.CommonTypes.Point
import de.thm.move.models.ModelicaCodeGenerator.FormatSrc
import de.thm.move.models.ModelicaCodeGenerator.FormatSrc.FormatSrc
import de.thm.move.util.PointUtils._
import de.thm.move.util.ResourceUtils
import de.thm.move.views.shapes._

class ModelicaCodeGenerator(
    srcFormat:FormatSrc,
    pxPerMm:Int,
    paneWidth:Double,
    paneHeight:Double) {
  type Lines = List[String]
  val encoding = Charset.forName("UTF-8")

  private def convertVal(v:Double):Double = v/pxPerMm
  private def convertPoint(p:Point):Point = p.map(convertVal)

  private def genOrigin(x:Double, y:Double): String =
    s"""origin=${genPoint(x,y)}"""

  private def genPoints(ps: Seq[Point]):String = {
    val psStrings = ps.map (genPoint(_)+",").mkString.dropRight(1)
    s"""points = {$psStrings}"""
  }

  private def genColor(name:String, p:Paint):String = p match {
    case c:Color =>
    s"""${name} = {${(c.getRed*255).toInt},${(c.getGreen*255).toInt},${(c.getBlue*255).toInt}}"""
    case _ => throw new IllegalArgumentException("Can't create rgb-values from non-color paint-values")
  }

  private def genStrokeWidth(elem:ColorizableShape, key:String="lineThickness"): String =
    s"$key = ${elem.getStrokeWidth}"

  private def genPoint(p:Point):String = {
    val convP = convertPoint(p)
    s"{${convP.x.toInt},${convP.y.toInt}}"
  }
  private def genPoint(x:Double,y:Double):String = genPoint((x,y))

  private def genFillAndStroke(shape:ColorizableShape)(implicit indentIdx:Int):String = {
    val strokeColor = genColor("lineColor", shape.getStrokeColor)
    val fillColor = genColor("fillColor", shape.oldFillColorProperty.get)
    val thickness = genStrokeWidth(shape)
    val linePattern = genLinePattern(shape)

    s"""${spaces}${strokeColor},
    |${spaces}${fillColor},
    |${spaces}${thickness},
    |${spaces}${linePattern}""".stripMargin.replaceAll("\n", linebreak)
  }

  private def genLinePattern(shape:ColorizableShape):String = {
    val linePattern = LinePattern.toString + "." + shape.linePattern.get.toString
    s"pattern = ${linePattern}"
  }

  private def genFillPattern(shape:ColorizableShape):String = {
    val fillPattern = FillPattern.toString + "." + shape.fillPatternProperty.get
    s"fillPattern = ${fillPattern}"
  }

  def generateShape[A <: Node]
    (shape:A, modelname:String, target:URI)(indentIdx:Int): String = shape match {
    case rectangle:ResizableRectangle =>
      val newY = paneHeight - rectangle.getY
      val endY = newY - rectangle.getHeight
      val endBottom = genPoint(rectangle.getBottomRight.x, endY)
      val start = genPoint(rectangle.getX, newY)
      val fillPattern = genFillPattern(rectangle)

      implicit val newIndentIdx = indentIdx + 2
      val colors = genFillAndStroke(rectangle)

      s"""${spaces(indentIdx)}Rectangle(
         |${colors},
         |${spaces}${fillPattern},
         |${spaces}extent = {$start, $endBottom}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case circle:ResizableCircle =>
      val angle = "endAngle = 360"

      val bounding = circle.getBoundsInLocal
      val newY = paneHeight - bounding.getMinY
      val endY = newY - bounding.getHeight
      val start = genPoint(bounding.getMinX, newY)
      val end = genPoint(bounding.getMaxX, endY)
      val fillPattern = genFillPattern(circle)
      implicit val newIndentIdx = indentIdx + 2
      val colors = genFillAndStroke(circle)


      s"""${spaces(indentIdx)}Ellipse(
          |${colors},
          |${spaces}${fillPattern},
          |${spaces}extent = {$start,$end},
          |${spaces}$angle
          |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case line:ResizableLine =>
      //offset, if element was moved (=0 if not moved)
      val offsetX = line.getLayoutX
      val offsetY = line.getLayoutY
      val pointList = List(
        (line.getStartX + offsetX, paneHeight - (line.getStartY + offsetY)),
        (line.getEndX + offsetX, paneHeight - (line.getEndY + offsetY))
      )
      val points = genPoints( pointList )
      val color = genColor("color", line.getStrokeColor)
      val thickness = genStrokeWidth(line, "thickness")
      val linePattern = genLinePattern(line)

      implicit val newIndentIdx = indentIdx + 2

      s"""${spaces(indentIdx)}Line(
         |${spaces}${points},
         |${spaces}${color},
         |${spaces}${linePattern},
         |${spaces}${thickness}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)

    case path:ResizablePath =>
      val offsetX = path.getLayoutX
      val offsetY = path.getLayoutY
      val points = genPoints(path.allElements.flatMap {
        case move:MoveTo =>
          val point = ( move.getX+offsetX, paneHeight-(move.getY+offsetY) )
          List( point )
        case line:LineTo =>
          val point = ( line.getX+offsetX, paneHeight-(line.getY+offsetY) )
          List( point )
      })

      val color = genColor("color", path.getStrokeColor)
      val thickness = genStrokeWidth(path, "thickness")
      val linePattern = genLinePattern(path)

      implicit val newIndentIdx = indentIdx + 2

      s"""${spaces(indentIdx)}Line(
         |${spaces}${points},
         |${spaces}${linePattern},
         |${spaces}${color},
         |${spaces}${thickness}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case polygon:ResizablePolygon =>
      //offset, if element was moved (=0 if not moved)
      val offsetX = polygon.getLayoutX
      val offsetY = polygon.getLayoutY
      val edgePoints = for {
        idx <- 0 until polygon.getPoints.size by 2
        x = polygon.getPoints.get(idx).toDouble
        y = polygon.getPoints.get(idx+1).toDouble
      } yield (x+offsetX,paneHeight-(y+offsetY))

      val points = genPoints(edgePoints)
      val fillPattern = genFillPattern(polygon)

      implicit val newIndentIdx = indentIdx + 2
      val colors = genFillAndStroke(polygon)

      s"""${spaces(indentIdx)}Polygon(
         |${spaces}${points},
         |${spaces}${colors},
         |${spaces}${fillPattern}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)

    case curve:QuadCurvePolygon =>
      val offsetX = curve.getLayoutX
      val offsetY = curve.getLayoutY
      val edgePoints = for(point <- curve.getUnderlyingPolygonPoints)
        yield (point.x+offsetX, paneHeight - (point.y+offsetY))
      val points = genPoints(edgePoints)
      val fillPattern = genFillPattern(curve)

      implicit val newIndentIdx = indentIdx + 2
      val colors = genFillAndStroke(curve)

      s"""${spaces(indentIdx)}Polygon(
         |${spaces}${points},
         ${colors},
         |${spaces}${fillPattern},
         |${spaces}smooth = Smooth.Bezier
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case curvedL:QuadCurvePath =>
      val offsetX = curvedL.getLayoutX
      val offsetY = curvedL.getLayoutY
      val edgePoints = for(point <- curvedL.getUnderlyingPolygonPoints)
        yield (point.x+offsetX, paneHeight - (point.y+offsetY))
      val points = genPoints(edgePoints)
      val color = genColor("color", curvedL.getStrokeColor)
      val thickness = genStrokeWidth(curvedL, "thickness")
      val linePattern = genLinePattern(curvedL)

      implicit val newIndentIdx = indentIdx + 2

      s"""${spaces(indentIdx)}Line(
         |${spaces}${points},
         |${spaces}${color},
         |${spaces}${linePattern},
         |${spaces}${thickness},
         |${spaces}smooth = Smooth.Bezier
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)

    case resImg:ResizableImage =>
      val bounding = resImg.getBoundsInLocal
      val newY = paneHeight - bounding.getMinY
      val endY = newY - bounding.getHeight
      val start = genPoint(bounding.getMinX, newY)
      val end = genPoint(bounding.getMinX + bounding.getWidth, endY)

      implicit val newIndentIdx = indentIdx + 2

      val imgStr = resImg.srcEither match {
        case Left(uri) =>
          copyImg(uri, target)
          val filename = ResourceUtils.getFilename(uri)
          s"""fileName = "modelica://$modelname/$filename""""
        case Right(bytes) =>
          val encoder = Base64.getEncoder
          val encodedBytes = encoder.encode(bytes)
          val byteStr = new String(encodedBytes, "UTF-8")
          s"""imageSource = "$byteStr""""
      }

      s"""${spaces(indentIdx)}Bitmap(
         |${spaces}extent = {${start}, ${end}},
         |${spaces}${imgStr}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case text:ResizableText =>
      val bounding = text.getBoundsInParent
      val newY = paneHeight - bounding.getMinY
      val endY = newY - bounding.getHeight
      val start = genPoint(text.getX.toInt, newY.toInt)
      val end = (text.getX.toInt + bounding.getWidth.toInt, endY.toInt)
      val str = text.getText
      val size = text.getSize
      val font = text.getFont
      val fontName = font.getName
      val style = ""
      val color = genColor("textColor", text.getFontColor)
      val alignment = "TextAlignment." + (text.getTextAlignment match {
        case TextAlignment.LEFT => "Left"
        case TextAlignment.CENTER => "Center"
        case TextAlignment.RIGHT => "Right"
        case _ => throw new IllegalArgumentException("Can't generate TextAlignment for: "+
          text.getTextAlignment)
      })

      implicit val newIndentIdx = indentIdx + 2

      s"""${spaces(indentIdx)}Text(
         |${spaces}extent = {${start},${end}},
         |${spaces}textString = "${str}",
         |${spaces}fontSize = ${size},
         |${spaces}fontName = "${fontName}",
         |${spaces}textStyle = ${style},
         |${spaces}${color},
         |${spaces}horizontalAlignment = ${alignment}
         |${spaces(indentIdx)})""".stripMargin.replaceAll("\n", linebreak)
    case _ => throw new IllegalArgumentException(s"Can't generate mdoelica code for: $shape")
  }


  private def copyImg(src:URI, target:URI): Unit = {
    val targetPath = Paths.get(target).getParent
    val srcPath = Paths.get(src)
    val filename = srcPath.getFileName
    Files.copy(srcPath, targetPath.resolve(filename))
  }


  private def generateIcons[A <: Node](modelname:String, target:URI, shapes:List[A]): Lines = {
    val systemStartpoint = genPoint((0.0,0.0))
    val systemEndpoint = genPoint((paneWidth, paneHeight))

    val iconStr =
      s"""${spaces(2)}Icon (
      |${spaces(4)}coordinateSystem(
      |${spaces(6)}extent = {${systemStartpoint},$systemEndpoint}
      |${spaces(4)}),""".stripMargin.replaceAll("\n", linebreak)

    val graphicsStart = s"${spaces(4)}graphics = {"
    val shapeStr = shapes.zipWithIndex.map {
      case (e,idx) if idx < shapes.length-1 =>
        generateShape(e, modelname, target)(6) + ","
      case (e,_) => generateShape(e, modelname, target)(6)
    }
    iconStr :: graphicsStart :: shapeStr ::: List(s"${spacesOrNothing(4)}})")
  }

  def generate[A <: Node](modelname:String, target:URI, shapes:List[A]): Lines = {
    val header = generateHeader(modelname)(2)
    val footer = generateFooter(modelname)(2)

    val graphics = generateIcons(modelname, target, shapes) ::: List(footer)
    header :: graphics
  }

  def generateExistingFile[A <: Node](modelname:String, target:URI, shapes:List[A]): Lines = {
    val graphics = generateIcons(modelname, target, shapes)
    graphics
  }

  def writeToFile(beforeIcons:String, lines:Lines, afterIcons:String)(target:URI): Unit = {
    val path = Paths.get(target)
    val writer = Files.newBufferedWriter(path, encoding)

    try {
      val str = lines.mkString(linebreakOrNothing)
      writer.write(beforeIcons + linebreakOrNothing)
      writer.write(str)
      writer.write(afterIcons)
      writer.write("\n")
    } finally {
      writer.close()
    }
  }

  private def generateHeader(modelName:String)(implicit indentIdx:Int):String =
      s"model $modelName\n" +
        spacesOrNothing + "annotation("

  private def generateFooter(modelName:String)(implicit indentIdx:Int):String =
      spaces + ");\n" +
        s"end $modelName;"

  private def spacesOrNothing(implicit indent:Int): String = srcFormat match {
    case FormatSrc.Pretty => spaces
    case FormatSrc.Oneline => ""
  }

  private def spaces(implicit indent:Int): String = srcFormat match {
    case FormatSrc.Pretty => (for(_ <- 0 until indent) yield " ").mkString("")
    case FormatSrc.Oneline => " "
  }

  private def linebreak: String = srcFormat match {
    case FormatSrc.Oneline => " "
    case _ => "\n"
  }

  private def linebreakOrNothing:String = srcFormat match {
    case FormatSrc.Oneline => ""
    case _ => "\n"
  }
}


object ModelicaCodeGenerator {
  object FormatSrc extends Enumeration {
    type FormatSrc = Value
    val Oneline, Pretty = Value
  }
}
