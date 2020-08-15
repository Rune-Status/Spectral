package org.spectral.deobfuscator

import org.junit.jupiter.api.Test
import java.applet.Applet
import java.applet.AppletContext
import java.applet.AppletStub
import java.awt.Color
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLClassLoader
import javax.swing.JFrame

class GamepackJarTest {

    @Test
    fun `test gamepack jar`() {
        val jar = File("../gamepack-deob-190.jar")

        val frame = JFrame()
        frame.layout = GridLayout(1, 0)
        frame.title = "Deobfusctor Test Client"

        val applet = this.createApplet(jar)
        frame.add(applet)

        frame.pack()

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        while(true) {}
    }

    private fun crawl(): Map<String, String> {
        val params = hashMapOf<String, String>()
        val lines = URL("http://oldschool.runescape.com/jav_config.ws").readText().split("\n")

        lines.forEach {
            var line = it
            if(line.startsWith("param=")) {
                line = line.substring(6)
            }
            val idx = line.indexOf("=")
            if(idx >= 0) {
                params[line.substring(0,idx)] = line.substring(idx + 1)
            }
        }

        return params
    }

    private fun createApplet(jar: File): Applet {
        if(!jar.exists()) throw FileNotFoundException()
        val params = crawl()
        val classloader = URLClassLoader(arrayOf(jar.toURI().toURL()))
        val initialClass = params["initial_class"]!!.replace(".class", "")
        val applet = classloader.loadClass(initialClass).newInstance() as Applet
        applet.background = Color.BLACK
        applet.preferredSize = Dimension(params["applet_minwidth"]!!.toInt(), params["applet_minheight"]!!.toInt())
        applet.size = applet.preferredSize
        applet.layout = null
        applet.setStub(createAppletStub(applet, params))
        applet.isVisible = true
        applet.init()
        return applet
    }

    private fun createAppletStub(applet: Applet, params: Map<String, String>): AppletStub {
        return object : AppletStub {
            override fun isActive(): Boolean = true
            override fun getDocumentBase(): URL = URL(params["codebase"])
            override fun getCodeBase(): URL = URL(params["codebase"])
            override fun appletResize(width: Int, height: Int) { applet.size = Dimension(width, height) }
            override fun getParameter(name: String): String? = params[name]
            override fun getAppletContext(): AppletContext? = null
        }
    }
}