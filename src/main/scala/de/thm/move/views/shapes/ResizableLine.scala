package de.thm.move.views.shapes

import javafx.scene.shape.Line

import de.thm.move.models.CommonTypes.Point
import de.thm.move.views.Anchor

class ResizableLine(
         start:Point,
         end:Point,
         strokeSize:Int) extends Line(start._1, start._2, end._1, end._2) with ResizableShape with ColorizableShape {
  setStrokeWidth(strokeSize)

  val startAnchor = new Anchor(start)
  val endAnchor = new Anchor(end)
  val getAnchors: List[Anchor] = List(startAnchor, endAnchor)

  bindAnchorsTranslationToShapesLayout(this)(getAnchors:_*)

  startXProperty().bind(startAnchor.centerXProperty())
  startYProperty().bind(startAnchor.centerYProperty())
  endXProperty().bind(endAnchor.centerXProperty())
  endYProperty().bind(endAnchor.centerYProperty())
}