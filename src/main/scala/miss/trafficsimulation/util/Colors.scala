package miss.trafficsimulation.util

import scala.language.implicitConversions

case class Color(r: Int, g: Int, b: Int) extends Serializable

object Color {
  implicit def toColor(color: Color): swing.Color = {
    new swing.Color(color.r, color.g, color.b)
  }

  def getColorFromHTMLHex(htmlHex: String): Color = {
    val awtColor = java.awt.Color.decode(htmlHex)
    Color(awtColor.getRed, awtColor.getGreen, awtColor.getBlue)
  }
}

object CommonColors {
  val Red = Color(255, 0, 0)
  val Green = Color(0, 255, 0)
  val Blue = Color(0, 0, 255)
  val Cyan = Color(0, 255, 255)
  val Magenta = Color(255, 0, 255)
  val Yellow = Color(255, 255, 0)
  val White = Color(255, 255, 255)
  val Black = Color(0, 0, 0)
}