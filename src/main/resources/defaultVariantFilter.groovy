import org.drpowell.vcf.*

headers = ['#FILTER=<ID=UnlikelyDeleterious,Description="Unlikely to be deleterious">',
           '#FILTER=<ID=Common,Description="MAF >= 1% in some population">']

// the passed "v" will be of type VCFVariant
// if the function "filter" returns "true", then the variant will be returned by the iterator
def filter(VCFVariant v) {
	def cutoff = 0.01
	if (!filterImpact(v)) { v.addFilter("UNLIKELY_DELETERIOUS"); }
	if (!(v.getInfoValue("NIEHSAF")?.toFloat() <= cutoff &&
	    v.getInfoValue("NIEHSIAF")?.toFloat() <= cutoff &&
	    v.getInfoValue("TGAF")?.toFloat() <= cutoff) &&
		all_ESP(cutoff)) {
		v.addFilter("COMMON")
	}
	return v
}

def numeric(x) {
	if (x == null || x == '.' || x == '') return 0;
	return x.toFloat()
}

def filterImpact(v) {
	def impact = v.getInfoValue("IMPACT");
	if (impact == null) { return false; }
	return (impact.equals("HIGH") || impact.equals("MODERATE"))
}

def all_ESP(v, cutoff) {
	def mafs = v.getInfoValue("ESPMAF")
	if (mafs == null) {
		return true
	}
	return mafs.split(',').every { numeric(it) <= 100*cutoff }
}
