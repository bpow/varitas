addVCFAnnotator("/home/bpow/anno/niehs/niehs.95samples.polymorphic.filtered.indels.vcf", "AF=NIEHSAF")
addVCFAnnotator("/home/bpow/anno/niehs/niehs.95samples.polymorphic.filtered.snps.vcf", "AF=NIEHSAF")
addVCFAnnotator("/home/bpow/anno/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")
addVCFAnnotator("/home/bpow/anno/dbsnp135ncbi.vcf.gz", 
    "SCS,GMAF,CLN,PM,G5A,G5")
addVCFAnnotator("/home/bpow/anno/ESP5400.vcf.gz",
    "GTS,EA_GTC,AA_GTC,FG,GM,AA,AAC,PP,CDP,CG,GS,CA,DP=ESPDP,GL=ESPGL")
addVCFAnnotator("/home/bpow/anno/Complete_Public_Genomes_54genomes_VQHIGH.vcf.gz",
    "AF=CG54AF").setAddChr(true).setRequirePass(true)
addTSVAnnotator("/home/bpow/anno/dbNSFP1.3/dbNSFP1.3.gz",
    "17=PHYLOP,18=PHYLOP_PRED,19=SIFT,20=SIFT_PRED,21=PP2,22=PP2_PRED,23=LRT,24=LRT_PRED,25=MT,26=MT_PRED").checkRef(3).checkAlt(4)
