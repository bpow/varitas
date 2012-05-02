package org.drpowell.grandannotator;

import java.util.concurrent.ConcurrentMap;

public interface Annotator {

	public abstract ConcurrentMap<String, Object> annotate(VCFVariant var);

	public abstract ConcurrentMap<String, Object> annotate(final String chromosome,
			final int start, final int end, final String ref, final String alt,
			ConcurrentMap<String, Object> info);

}