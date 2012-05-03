package org.drpowell.grandannotator;

import java.util.Map;

public interface Annotator {

	public abstract Map<String, Object> annotate(VCFVariant var);

	public abstract Map<String, Object> annotate(final String chromosome,
			final int start, final int end, final String ref, final String alt,
			Map<String, Object> info);

	public abstract Iterable<String> infoLines();

}