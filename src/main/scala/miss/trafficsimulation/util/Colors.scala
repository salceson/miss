package miss.trafficsimulation.util

class Color(val r: Int, val g: Int, val b: Int)

object Color {
  def apply(r: Int, g: Int, b: Int): Color = new Color(r, g, b)

  def unapply(c: Color): Option[(Int, Int, Int)] = Some((c.r, c.g, c.b))
}

case object Red extends Color(255, 0, 0)

case object Green extends Color(0, 255, 0)

case object Blue extends Color(0, 0, 255)

case object Cyan extends Color(0, 255, 255)

case object Magenta extends Color(255, 0, 255)

case object Yellow extends Color(255, 255, 0)

case object White extends Color(255, 255, 255)

case object Black extends Color(0, 0, 0)
