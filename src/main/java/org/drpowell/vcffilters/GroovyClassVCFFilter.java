package org.drpowell.vcffilters;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;

import java.io.File;
import java.io.IOException;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;

public class GroovyClassVCFFilter extends VCFFilteringIterator {
	private GroovyObject delegate;

	public GroovyClassVCFFilter(VCFIterator client, File file) {
		super(client);
		CompilerConfiguration config = new CompilerConfiguration();
		config.addCompilationCustomizers(new ImportCustomizer().addStarImports(
				"org.drpowell.vcf", "org.drpowell.vcffilters"));
		GroovyClassLoader gcl = new GroovyClassLoader(this.getClass().getClassLoader(), config);
		Class clazz;
		try {
			clazz = gcl.parseClass(file);
			delegate = (GroovyObject) clazz.newInstance();
		} catch (CompilationFailedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public VCFVariant filter(VCFVariant variant) {
		if (delegate == null) return variant;
		return (VCFVariant) delegate.invokeMethod("filter", variant);
	}
}
