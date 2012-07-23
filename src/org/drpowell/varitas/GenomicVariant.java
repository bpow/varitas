package org.drpowell.varitas;

import java.util.Map;

public interface GenomicVariant {
	
	public String getSequence();
	public int getStart();
	public int getEnd();
	public String getRef();
	public String getAlt();
	
	public Map<String, Object> getInfo();

}
