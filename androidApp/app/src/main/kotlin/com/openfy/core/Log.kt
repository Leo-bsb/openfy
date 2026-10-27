package com.openfy.core

/** Log enxuto do app Android; o desktop usa o Log em arquivo. */
object Log {
    fun write(message: String) {
        android.util.Log.d("Openfy", message)
    }
}
