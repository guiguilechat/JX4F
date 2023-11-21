package fr.guiguilechat.jx4f.model.catalog;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fr.guiguilechat.jx4f.model.unpacker.X4Cache;
import fr.guiguilechat.jx4f.model.unpacker.data.CachedX4Data;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@AllArgsConstructor
public class X4Catalog {

	public static void main(String[] args) {
		X4Catalog model = X4Catalog.ofExtensionsContaining(X4Cache.INSTANCE, "ego_dlc");
		Document document = model.xmlDoc();
		NodeList rootChildren = document.getDocumentElement().getChildNodes();
		for (int i = 0; i < rootChildren.getLength(); i++) {
			Node child = rootChildren.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) child;
				System.out.println(e.getTagName() + " : " + e.getChildNodes().getLength());
			}
		}

	}

	@Getter
	private final List<CachedX4Data> usedData;

	public static X4Catalog ofExtensionsPredicate(X4Cache cache, Predicate<CachedX4Data> extDataFilter) {
		Stream<CachedX4Data> dataStream = cache.mainData().stream();
		dataStream = Stream.concat(dataStream,
				cache.extensionData().stream().filter(d -> extDataFilter == null || extDataFilter.test(d)));
		return new X4Catalog(dataStream.toList());
	}

	public static X4Catalog ofExtensionsContaining(X4Cache cache, String... allowedNames) {
		if (allowedNames == null || allowedNames.length == 0) {
			return ofExtensionsPredicate(cache, o -> false);
		}
		Predicate<CachedX4Data> extDataFilter = d -> d.getExtension() == null
				|| Stream.of(allowedNames).filter(s -> d.getExtension().contains(s)).findAny().isPresent();
		return ofExtensionsPredicate(cache, extDataFilter);
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final Transformer transformer = makeTransformer();

	protected Transformer makeTransformer() {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		transformerFactory.setAttribute("indent-number", 2);
		Transformer transformer;
		try {
			transformer = transformerFactory.newTransformer();
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		}
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		return transformer;
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final List<File> xmls = getUsedData().stream().flatMap(CachedX4Data::files)
			.filter(f -> f.getName().endsWith(".xml")).toList();

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final DocumentBuilderFactory domFactory = makeFactory();

	protected DocumentBuilderFactory makeFactory() {
		DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		try {
			domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		return domFactory;
	}

	@Getter(lazy = true)
	@Accessors(fluent = true)
	private final Document xmlDoc = loadXmlDoc();

	protected Document loadXmlDoc() {
		Document ret;
		try {
			ret = domFactory().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		Element root = ret.createElement("x4catalog");
		ret.appendChild(root);

		for (File xml : xmls()) {
			try {
				mergeXml(root, xml);
			} catch (ParserConfigurationException | SAXException | IOException e) {
				throw new RuntimeException(e);
			}
		}
		return ret;
	}

	protected void mergeXml(Element root, File xmlFile)
			throws ParserConfigurationException, SAXException, IOException {
		if (xmlFile.length() == 0) {
			return;
		}
		Element newElement = domFactory().newDocumentBuilder().parse(xmlFile).getDocumentElement();
		String rootTag = newElement.getTagName();
		switch (rootTag) {
			case "diff":
				applyDiff(root, newElement);
			break;
			default:
				appendXML(root, newElement);
		}
	}

	protected void appendXML(Element root, Element newElement) {
		String rootTag = newElement.getTagName();
		for (int i = 0; i < root.getChildNodes().getLength(); i++) {
			Node child = root.getChildNodes().item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element targetElement = (Element) child;
				if (targetElement.getTagName().equals(rootTag)) {
					for (int j = 0; j < newElement.getChildNodes().getLength(); j++) {
						targetElement.appendChild(
								root.getOwnerDocument().importNode(newElement.getChildNodes().item(j), true));
					}
					return;
				}
			}
		}
		// we found no corresponding element : create it
		root.appendChild(root.getOwnerDocument().importNode(newElement, true));
	}

	/**
	 * apply the diffs in an xml element to the root catalog
	 */
	protected void applyDiff(Element catalog, Element diffRoot) {
		for (int i = 0; i < diffRoot.getChildNodes().getLength(); i++) {
			Node child = diffRoot.getChildNodes().item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				Element diffElement = (Element) child;
				switch (diffElement.getTagName()) {
					case "add":
						applyAdd(catalog, diffElement);
					break;
					case "replace":
						applyReplace(catalog, diffElement);
					break;
					case "remove":
						applyRemove(catalog, diffElement);
					break;
					default:
						throw new UnsupportedOperationException(diffElement.getTagName() + " not handled in switch");
				}
			}
		}
	}

	/** apply an add diff to the catalog */
	protected void applyAdd(Element catalog, Element addElement) {
		String sel = addElement.getAttribute("sel");
		System.out.println("add , sel=" + sel);
		addElement.normalize();
		try {
			transformer().transform(new DOMSource(addElement), new StreamResult(System.out));
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		}
		// TODO Auto-generated method stub
	}

	/** apply a replace diff to the catalog */
	protected void applyReplace(Element catalog, Element replaceElement) {
		// TODO Auto-generated method stub
	}

	/** apply a remove diff to the catalog */
	protected void applyRemove(Element catalog, Element RemoveElement) {
		// TODO Auto-generated method stub
	}

}
