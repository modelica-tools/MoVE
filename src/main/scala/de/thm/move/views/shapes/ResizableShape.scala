/**
 * Copyright (C) 2016 Nicola Justus <nicola.justus@mni.thm.de>
 */


package de.thm.move.views.shapes

import javafx.scene.Node
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.input.KeyEvent
import javafx.scene.input.KeyCode
import de.thm.move.views.Anchor
import de.thm.move.Global
import de.thm.move.controllers.implicits.FxHandlerImplicits._
import de.thm.move.models.CommonTypes._

trait ResizableShape extends Node {

  val selectionRectangle = new SelectionRectangle(this)

  val getAnchors: List[Anchor]

  def getX: Double
  def getY: Double
  final def getXY: Point = (getX, getY)

  def setX(x:Double): Unit
  def setY(y:Double): Unit
  final def setXY(p:Point): Unit = {
    setX(p._1)
    setY(p._2)
  }
}
