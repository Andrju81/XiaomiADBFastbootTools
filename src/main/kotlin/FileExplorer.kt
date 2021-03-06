import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.ProgressBar
import javafx.scene.control.TextField
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class FileExplorer(var status: TextField, var progress: ProgressBar) : Command() {

    var path = "/"
    lateinit var t: Thread

    init {
        pb.redirectErrorStream(false)
    }

    fun makeFile(out: String): AndroidFile? {
        val bits = ArrayList<String>()
        for (bit in out.split(' '))
            if (bit.isNotEmpty()) {
                if (bit == "->")
                    break
                bits.add(bit)
            }
        if (bits.size < 8)
            return null
        var name = ""
        var cnt = 7
        while (cnt != bits.size)
            name += "${bits[cnt++]} "
        return AndroidFile(bits[0][0] != '-', name.trim(), bits[4].toInt(), "${bits[5]} ${bits[6]}")
    }

    fun navigate(where: String) {
        if (where == "..") {
            if (path.split('/').size < 3)
                return
            path = path.substring(0, path.lastIndex).substringBeforeLast('/') + "/"
        } else path += "$where/"
    }

    fun getFiles(): ObservableList<AndroidFile> {
        val lines = exec("adb shell ls -l $path", 5)
        val files = FXCollections.observableArrayList<AndroidFile>()
        for (line in lines.split('\n')) {
            if (line.contains(':') && !line.contains("ls:")) {
                val file = makeFile(line)
                if (file != null)
                    files.add(file)
            }
        }
        return files
    }

    private fun format(pathname: String): String = "'$pathname'"

    private fun init(command: String = "adb") {
        status.text = ""
        try {
            proc = pb.start()
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        scan = Scanner(proc.inputStream).useDelimiter("")
        while (scan.hasNextLine()) {
            val line = scan.nextLine()
            Platform.runLater {
                if (line.contains('%'))
                    progress.progress = line.substring(line.indexOf('[') + 1, line.indexOf('%')).trim().toInt() / 100.0
                else if (line.contains(command))
                    status.text = "ERROR: ${line.substringAfterLast(':').trim()}"
            }
        }
        scan.close()
        proc.waitFor()
    }

    fun pull(selected: List<AndroidFile>, to: File, func: () -> Unit) {
        t = Thread {
            if (selected.isEmpty()) {
                arguments = arrayOf("${prefix}adb", "pull", path, to.absolutePath)
                pb.command(*arguments)
                init()
            } else {
                for (file in selected) {
                    arguments = arrayOf("${prefix}adb", "pull", path + file.name, to.absolutePath)
                    pb.command(*arguments)
                    init()
                }
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun push(selected: List<File>, func: () -> Unit) {
        t = Thread {
            for (file in selected) {
                arguments = arrayOf("${prefix}adb", "push", file.absolutePath, path)
                pb.command(*arguments)
                init()
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun delete(selected: List<AndroidFile>, func: () -> Unit) {
        t = Thread {
            for (file in selected) {
                if (file.dir)
                    arguments = arrayOf("${prefix}adb", "shell", "rm", "-rf", format(path + file.name))
                else arguments = arrayOf("${prefix}adb", "shell", "rm", "-f", format(path + file.name))
                pb.command(*arguments)
                init("rm")
            }
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun mkdir(name: String, func: () -> Unit) {
        t = Thread {
            arguments = arrayOf("${prefix}adb", "shell", "mkdir", format(path + name))
            pb.command(*arguments)
            init("mkdir")
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun rename(selected: AndroidFile, to: String, func: () -> Unit) {
        t = Thread {
            arguments = arrayOf("${prefix}adb", "shell", "mv", format(path + selected.name), format(path + to))
            pb.command(*arguments)
            init("mv")
            Platform.runLater {
                if (status.text.isEmpty())
                    status.text = "Done!"
                progress.progress = 0.0
                func()
            }
        }
        t.isDaemon = true
        t.start()
    }

}