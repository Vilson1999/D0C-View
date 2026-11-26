package com.example.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.InputStream
import java.io.Reader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-JDK DOM-backed [XmlPullParser] implementation used as a fallback on plain JVM
 * unit-test environments where `android.util.Xml` / `XmlPullParserFactory` are stubbed.
 */
internal class DomXmlPullParser : XmlPullParser {

    private data class Event(
        val type: Int,
        val depth: Int,
        val name: String? = null,
        val text: String? = null,
        val attrs: List<Pair<String, String>> = emptyList()
    )

    private var events: List<Event> = listOf(Event(XmlPullParser.START_DOCUMENT, 0))
    private var cursor = 0

    override fun setInput(inputStream: InputStream?, inputEncoding: String?) {
        val stream = inputStream ?: throw XmlPullParserException("Null InputStream")
        val source = InputSource(stream).apply {
            if (!inputEncoding.isNullOrBlank()) encoding = inputEncoding
        }
        parseSource(source)
    }

    override fun setInput(`in`: Reader?) {
        val reader = `in` ?: throw XmlPullParserException("Null Reader")
        parseSource(InputSource(reader))
    }

    private fun parseSource(source: InputSource) {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val doc = dbf.newDocumentBuilder().parse(source)
        val built = ArrayList<Event>()
        built.add(Event(XmlPullParser.START_DOCUMENT, 0))
        doc.documentElement?.let { emitNode(it, 1, built) }
        built.add(Event(XmlPullParser.END_DOCUMENT, 0))
        events = built
        cursor = 0
    }

    private fun emitNode(node: Node, depth: Int, out: MutableList<Event>) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val elem = node as Element
                val attrMap = elem.attributes
                val attrs = ArrayList<Pair<String, String>>(attrMap.length)
                for (i in 0 until attrMap.length) {
                    val attr = attrMap.item(i)
                    attrs.add(attr.nodeName to (attr.nodeValue ?: ""))
                }
                out.add(Event(XmlPullParser.START_TAG, depth, name = elem.tagName, attrs = attrs))
                val children = elem.childNodes
                for (i in 0 until children.length) {
                    emitNode(children.item(i), depth + 1, out)
                }
                out.add(Event(XmlPullParser.END_TAG, depth, name = elem.tagName))
            }
            Node.TEXT_NODE -> {
                val txt = node.nodeValue
                if (!txt.isNullOrEmpty()) {
                    out.add(Event(XmlPullParser.TEXT, depth - 1, text = txt))
                }
            }
            Node.CDATA_SECTION_NODE -> {
                val txt = node.nodeValue
                if (!txt.isNullOrEmpty()) {
                    out.add(Event(XmlPullParser.CDSECT, depth - 1, text = txt))
                }
            }
        }
    }

    private fun current(): Event = events.getOrElse(cursor) { events.last() }

    override fun getEventType(): Int = current().type

    override fun next(): Int {
        if (cursor < events.lastIndex) cursor++
        return current().type
    }

    override fun nextToken(): Int = next()

    override fun nextText(): String {
        if (current().type != XmlPullParser.START_TAG) {
            throw XmlPullParserException("Parser must be on START_TAG to read nextText()")
        }
        var ev = next()
        if (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
            val sb = StringBuilder(current().text ?: "")
            ev = next()
            while (ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) {
                sb.append(current().text ?: "")
                ev = next()
            }
            if (ev != XmlPullParser.END_TAG) {
                throw XmlPullParserException("Expected END_TAG after text")
            }
            return sb.toString()
        } else if (ev == XmlPullParser.END_TAG) {
            return ""
        }
        throw XmlPullParserException("Unexpected event $ev in nextText()")
    }

    override fun nextTag(): Int {
        var ev = next()
        if ((ev == XmlPullParser.TEXT || ev == XmlPullParser.CDSECT) && isWhitespace()) {
            ev = next()
        }
        if (ev != XmlPullParser.START_TAG && ev != XmlPullParser.END_TAG) {
            throw XmlPullParserException("Expected start or end tag")
        }
        return ev
    }

    override fun require(type: Int, namespace: String?, name: String?) {
        val cur = current()
        if (cur.type != type || (name != null && cur.name != name)) {
            throw XmlPullParserException("Expected type=$type name=$name")
        }
    }

    override fun getDepth(): Int = current().depth
    override fun getName(): String? = current().name
    override fun getText(): String? = current().text
    override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray? {
        val chars = current().text?.toCharArray() ?: return null
        if (holderForStartAndLength != null && holderForStartAndLength.size >= 2) {
            holderForStartAndLength[0] = 0
            holderForStartAndLength[1] = chars.size
        }
        return chars
    }

    override fun isWhitespace(): Boolean = current().text?.all { it.isWhitespace() } ?: false
    override fun getAttributeCount(): Int = current().attrs.size
    override fun getAttributeName(index: Int): String = current().attrs[index].first
    override fun getAttributeValue(index: Int): String = current().attrs[index].second
    override fun getAttributeValue(namespace: String?, name: String?): String? {
        if (name == null) return null
        return current().attrs.firstOrNull { it.first == name }?.second
    }

    override fun setFeature(name: String?, state: Boolean) = Unit
    override fun getFeature(name: String?): Boolean = false
    override fun setProperty(name: String?, value: Any?) = Unit
    override fun getProperty(name: String?): Any? = null
    override fun getInputEncoding(): String? = "UTF-8"
    override fun defineEntityReplacementText(entityName: String?, replacementText: String?) = Unit
    override fun getNamespaceCount(depth: Int): Int = 0
    override fun getNamespacePrefix(pos: Int): String? = null
    override fun getNamespaceUri(pos: Int): String? = null
    override fun getNamespace(prefix: String?): String? = null
    override fun getNamespace(): String = ""
    override fun getPrefix(): String? = null
    override fun isEmptyElementTag(): Boolean = false
    override fun getAttributeNamespace(index: Int): String = ""
    override fun getAttributePrefix(index: Int): String? = null
    override fun getAttributeType(index: Int): String = "CDATA"
    override fun isAttributeDefault(index: Int): Boolean = false
    override fun getPositionDescription(): String = "event=$cursor"
    override fun getLineNumber(): Int = -1
    override fun getColumnNumber(): Int = -1
}
