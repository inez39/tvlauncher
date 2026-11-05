package com.inez.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Ícones desenhados à mão (sem ficheiros de imagem). */
class IconView(ctx: Context, private val kind: Int, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        p.color = color
        p.strokeWidth = s * 0.075f
        when (kind) {
            KIND_TV -> {
                c.drawRoundRect(RectF(.1f * s, .18f * s, .9f * s, .68f * s), .08f * s, .08f * s, p)
                c.drawLine(.32f * s, .88f * s, .68f * s, .88f * s, p)
                c.drawLine(.5f * s, .68f * s, .5f * s, .88f * s, p)
            }
            KIND_HDMI -> {
                val path = Path().apply {
                    moveTo(.12f * s, .3f * s); lineTo(.88f * s, .3f * s); lineTo(.88f * s, .6f * s)
                    lineTo(.76f * s, .74f * s); lineTo(.24f * s, .74f * s); lineTo(.12f * s, .6f * s); close()
                }
                c.drawPath(path, p)
                for (x in listOf(.32f, .5f, .68f)) c.drawLine(x * s, .42f * s, x * s, .58f * s, p)
            }
            else -> {
                c.drawRoundRect(RectF(.06f * s, .28f * s, .94f * s, .74f * s), .22f * s, .22f * s, p)
                c.drawLine(.26f * s, .42f * s, .26f * s, .6f * s, p)
                c.drawLine(.17f * s, .51f * s, .35f * s, .51f * s, p)
                c.drawPoint(.68f * s, .45f * s, p)
                c.drawPoint(.78f * s, .57f * s, p)
            }
        }
    }

    companion object {
        const val KIND_TV = 0
        const val KIND_HDMI = 1
        const val KIND_GAME = 2
    }
}

/** Desenho do tempo: sol, lua, nuvem, chuva, neve, trovoada. */
class WeatherIconView(ctx: Context) : View(ctx) {
    var kind = KIND_CLEAR
        set(v) { field = v; invalidate() }
    var night = false
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun cloud(c: Canvas, s: Float, dy: Float) {
        p.style = Paint.Style.STROKE
        p.color = 0xFF9A8B82.toInt()
        val path = Path().apply {
            moveTo(.28f * s, .72f * s + dy)
            quadTo(.10f * s, .72f * s + dy, .12f * s, .56f * s + dy)
            quadTo(.14f * s, .40f * s + dy, .32f * s, .42f * s + dy)
            quadTo(.36f * s, .20f * s + dy, .56f * s, .24f * s + dy)
            quadTo(.70f * s, .26f * s + dy, .70f * s, .42f * s + dy)
            quadTo(.90f * s, .42f * s + dy, .88f * s, .58f * s + dy)
            quadTo(.88f * s, .72f * s + dy, .72f * s + 0f, .72f * s + dy)
            close()
        }
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        p.strokeWidth = s * 0.065f
        when (kind) {
            KIND_CLEAR -> if (night) {
                p.style = Paint.Style.FILL
                p.color = 0xFFC98F5E.toInt()
                val a = Path().apply { addCircle(.46f * s, .5f * s, .28f * s, Path.Direction.CW) }
                val b = Path().apply { addCircle(.6f * s, .4f * s, .24f * s, Path.Direction.CW) }
                a.op(b, Path.Op.DIFFERENCE)
                c.drawPath(a, p)
            } else {
                p.style = Paint.Style.STROKE
                p.color = 0xFFC98F5E.toInt()
                c.drawCircle(.5f * s, .5f * s, .2f * s, p)
                for (i in 0 until 8) {
                    val ang = Math.toRadians(i * 45.0)
                    val cx = .5f * s
                    val cy = .5f * s
                    val x1 = cx + Math.cos(ang).toFloat() * .31f * s
                    val y1 = cy + Math.sin(ang).toFloat() * .31f * s
                    val x2 = cx + Math.cos(ang).toFloat() * .43f * s
                    val y2 = cy + Math.sin(ang).toFloat() * .43f * s
                    c.drawLine(x1, y1, x2, y2, p)
                }
            }
            KIND_CLOUD -> cloud(c, s, 0f)
            KIND_RAIN -> {
                cloud(c, s, -.08f * s)
                p.color = 0xFF7F9AA8.toInt()
                for (x in listOf(.34f, .5f, .66f)) c.drawLine(x * s, .72f * s, (x - .05f) * s, .86f * s, p)
            }
            KIND_SNOW -> {
                cloud(c, s, -.08f * s)
                p.style = Paint.Style.FILL
                p.color = 0xFF9DB0BA.toInt()
                for (x in listOf(.34f, .5f, .66f)) c.drawCircle(x * s, .8f * s, .035f * s, p)
                p.style = Paint.Style.STROKE
            }
            else -> {
                cloud(c, s, -.08f * s)
                p.color = 0xFFC98F5E.toInt()
                val bolt = Path().apply {
                    moveTo(.54f * s, .62f * s); lineTo(.44f * s, .78f * s)
                    lineTo(.54f * s, .78f * s); lineTo(.46f * s, .92f * s)
                }
                c.drawPath(bolt, p)
            }
        }
    }

    companion object {
        const val KIND_CLEAR = 0
        const val KIND_CLOUD = 1
        const val KIND_RAIN = 2
        const val KIND_SNOW = 3
        const val KIND_STORM = 4

        /** Converte o código do Open-Meteo no desenho. */
        fun kindFor(code: Int): Int = when (code) {
            0, 1 -> KIND_CLEAR
            2, 3, 45, 48 -> KIND_CLOUD
            71, 73, 75, 77, 85, 86 -> KIND_SNOW
            95, 96, 99 -> KIND_STORM
            in 51..67, in 80..82 -> KIND_RAIN
            else -> KIND_CLOUD
        }
    }
}
