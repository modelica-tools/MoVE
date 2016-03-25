/**
 * Copyright (C) 2016 Nicola Justus <nicola.justus@mni.thm.de>
 */


package de.thm.move.views.shapes

import javafx.scene.image.{ImageView, Image}
import javafx.scene.input.MouseEvent

import de.thm.move.controllers.implicits.FxHandlerImplicits._
import de.thm.move.util.BindingUtils
import de.thm.move.models.CommonTypes._
import de.thm.move.views.Anchor

class ResizableImage(img:Image) extends ImageView(img) with ResizableShape with BoundedAnchors {
  setPreserveRatio(true)
  setFitWidth(200)

  override def getWidth: Double = getFitWidth
  override def getHeight: Double = getFitHeight

  override def setWidth(w:Double): Unit = setFitWidth(w)
  override def setHeight(h:Double): Unit = setFitHeight(h)
}
