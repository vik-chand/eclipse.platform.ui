package org.eclipse.e4.ui.tests.css.swt.parser;

import java.io.IOException;
import java.io.StringReader;

import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.core.engine.CSSErrorHandler;
import org.eclipse.e4.ui.css.swt.engine.CSSSWTEngineImpl;
import org.eclipse.swt.widgets.Display;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.stylesheets.StyleSheet;

public final class ParserTestUtil {

	private static final CSSErrorHandler ERROR_HANDLER = new CSSErrorHandler() {
		public void error(Exception e) {
			throw new RuntimeException(e);
		}
	};

	private ParserTestUtil() {
		// prevent instantiation
	}

	public static CSSStyleSheet parseCss(String css)
			throws IOException {
		Display display = Display.getDefault();
		CSSEngine engine = new CSSSWTEngineImpl(display);
		engine.setErrorHandler(ERROR_HANDLER);
		StyleSheet result = engine.parseStyleSheet(new StringReader(css));
		return (CSSStyleSheet) result;
	}
}
