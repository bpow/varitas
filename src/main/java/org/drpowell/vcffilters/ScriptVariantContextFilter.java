package org.drpowell.vcffilters;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;
import org.drpowell.util.VCFHeaderLineParser;
import org.drpowell.vcf.VariantContextIterator;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;

public class ScriptVariantContextFilter extends VariantContextFilteringIterator {

	private Invocable invocable;
	private VCFHeader header;
	
	private Invocable initializeEngine(URL filterURL, String language) {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName(language);
		try {
			// FIXME-- why doesn't this work? Can I use a CompilerConfiguration with JSR223?
//			if ("groovy".equalsIgnoreCase(language)) {
//				engine.eval("import org.drpowell.vcf.*\nimport org.drpowell.varitas.*\nimport org.drpowell.vcffilters.*\n\n");
//			}
			engine.eval(new InputStreamReader(filterURL.openStream()));
		} catch (ScriptException e) {
			Logger.getLogger("VARITAS").severe("Error preparing filter from " +
					filterURL.toString() + ", will pass all variants!\n" + e);
			engine = null;
		} catch (IOException e) {
			Logger.getLogger("VARITAS").severe("Error reading filter from " +
					filterURL.toString() + ", will pass all variants!\n" + e);
			engine = null;
		}
		Object moreHeaders = engine.get("headers");
		if (moreHeaders != null) {
			VCFHeaderLineParser hlp = new VCFHeaderLineParser(VCFHeaderVersion.VCF4_2);
			if (moreHeaders instanceof String) {
				for (String header : ((String) moreHeaders).split("\n")) {
					this.header.addMetaDataLine(hlp.headerFromString(header));
				}
			} else if (moreHeaders instanceof List) {
				for (Object header : (List) moreHeaders) {
					this.header.addMetaDataLine(hlp.headerFromString(header.toString()));
				}
			} else {
				Logger.getLogger("VARITAS").warning("Unable to add header for script filter");
			}
		}
		return (Invocable) engine;
	}

	public ScriptVariantContextFilter(VariantContextIterator client, URL filterURL, String language) {
		super(client);
		header = client.getHeader();
		invocable = initializeEngine(filterURL, language);
	}

	@Override
	public VariantContext filter(VariantContext variant) {
		if (invocable == null) return variant;
		try {
			return (VariantContext) invocable.invokeFunction("filter", variant);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			return variant;
		} catch (ScriptException e) {
			e.printStackTrace();
			return variant;
		}
	}

}
