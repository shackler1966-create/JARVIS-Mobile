package com.example.jarvismobile

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val extras = n.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isNotBlank() || text.isNotBlank()) {
            recent.removeAll { it.packageName == n.packageName && it.id == n.id }
            recent.add(0, Notice(n.packageName, n.id, title, text, n.postTime))
            while (recent.size > 30) recent.removeAt(recent.lastIndex)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        recent.removeAll { it.packageName == n.packageName && it.id == n.id }
    }

    companion object {
        data class Notice(val packageName: String, val id: Int, val title: String, val text: String, val time: Long)
        val recent = CopyOnWriteArrayList<Notice>()

        fun summary(): String {
            if (recent.isEmpty()) return "لا توجد إشعارات حديثة متاحة."
            return recent.take(8).joinToString(". ") { item ->
                val app = item.packageName.substringAfterLast('.')
                val content = listOf(item.title, item.text).filter { it.isNotBlank() }.joinToString(": ")
                "$app: $content"
            }
        }
    }
}
