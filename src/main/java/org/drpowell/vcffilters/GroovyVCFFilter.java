package org.drpowell.vcffilters;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.logging.Logger;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFMeta;
import org.drpowell.vcf.VCFVariant;

public class GroovyVCFFilter extends VCFFilteringIterator {

	private GroovyObject filter;
	private Binding binding;
	private VCFHeaders headers;
	
	private GroovyObject initialize(File scriptFile) {
		CompilerConfiguration config = new CompilerConfiguration();
		config.addCompilationCustomizers(new ImportCustomizer().addStarImports(
				"org.drpowell.vcf", "org.drpowell.vcffilters"));
		GroovyClassLoader gcl = new GroovyClassLoader(this.getClass().getClassLoader(), config);
		try {
			Class filtClass = gcl.parseClass(scriptFile);
			filter = (GroovyObject) filtClass.newInstance();
		} catch (Exception e) {
			Logger.getLogger("VARITAS").warning("Error settup up filter from " + scriptFile.getAbsolutePath() + " will just pass all variants\n" + e.toString());
			e.printStackTrace();
		}
//		Object moreHeaders = filter.getProperty("headers");
//		if (moreHeaders != null) {
//			if (moreHeaders instanceof String) {
//				for (String header : ((String) moreHeaders).split("\n")) {
//					headers.add(new VCFMeta(header));
//				}
//			} else if (moreHeaders instanceof List) {
//				for (Object header : (List) moreHeaders) {
//					headers.add(new VCFMeta(header.toString()));
//				}
//			} else {
//				Logger.getLogger("VARITAS").warning("Unable to add headers for javascript filter");
//			}
//		}
		return filter;
	}

	public GroovyVCFFilter(VCFIterator client, File scriptFile) {
		super(client);
		headers = client.getHeaders();
		initialize(scriptFile);
	}

	@Override
	public VCFVariant filter(VCFVariant variant) {
		if (filter == null) return variant;
		return (VCFVariant) (filter.invokeMethod("filter", variant));
	}
}
