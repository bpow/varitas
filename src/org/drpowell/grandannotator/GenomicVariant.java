package org.drpowell.grandannotator;

public class GenomicVariant {
	public final String sequence;
	public final int start;
	public final int end;
	public final String ref;
	public final String alt;
	
	public GenomicVariant(String _sequence, int _start, String _ref, String _alt) {
		sequence = _sequence;
		start = _start;
		ref = _ref;
		alt = _alt;
		end = start + ref.length()-1;
	}
	
	public GenomicVariant(String _sequence, int _start, int _end) {
		sequence = _sequence;
		start = _start;
		end = _end;
		ref = null;
		alt = null;
	}

}
