VARITAS - Variant Annotation with Rapid Incorporation of Tabular Annotation Sources
=======================================================================================

VARITAS is designed to help people who are evaluating genome sequence variants.


Design Goals
------------

VARITAS aims to be:
* Flexible: Incorporate new data sources easily, change a text file rather
  than recompile.
* Portable: What if you prefer Linux, but your boss uses Macintoshes and
  everyone else in the lab has to use Windows because that is what IT
  provides them? VARITAS runs on the Java(tm) virtual machine, and can be
  installed as a single .jar file (but it needs data sources, of course...)
* (Reasonably) small: Reference data sets are compressed (using bgzip) to
  minimize storage space and disk IO. The references can be stored on the
  laptop you bring to lab meeting.
* (Reasonably) fast: Reference data sets are indexed using Heng Li's `tabix`
  R-tree based interval indexing. Some trade-offs for speed were made to
  maintain portability.

Compilation
-----------

A [gradle](http://gradle.org) build wrapper is provided. After code is checkout out from github, from within the working directory one can run:
    ./gradlew
(or `gradlew.bat` for windows systems). This will create an executatble .jar file as `varitas.jar`. This jar file includes all of the library dependencies, so it can be copied elsewhere (or to a different computer) and still run fine.

Usage:
------

If you just run `java -jar varitas.jar`, you get a list of command-line options:

    java -jar varitas.jar

Annotation configurations (`-c`) and filters (`-f` or `-j`) are processed in the order they are provided on the
command line, as are the output instructions (`-o` or `-x`). As a convienience, the option for input (`-i`) is moved
to the beginning before processing (since there's no point in filtering before you have input).

To annotate some variants:

    java -jar varitas.jar -c config.js -i input.vcf -o output.vcf

If you just want to use the default annotation configuration, use the `-C` option:

    java -jar varitas.jar -C -i input.vcf -o output.vcf

If no '-i' or '-o' options are given, then the standard input and standard output streams are used, so this works:

    zcat input.vcf.gz | java -jar Varitas -c config.js | bgzip -c > output.vcf.gz

By default, VCF output is provided. The `-x` option allows for output to an .xls spreadsheet file (with some niceties
such as links to dbSNP, OMIM, etc). Both `-x` and `-o` can be specified, and can even be specified multiple times
to allow for output at different stages of the analysis.

    java -jar varitas.jar -i input.vcf -F -o defaultResults.vcf -f anotherFilter.js -o moreStringentResults.vcf -x moreStringentResults.xls

Annotation config files:
---------------------

Since version 6.0, the standard Java runtime environment (JRE) comes with a javascript interpreter. Varitas uses this to process configuration files, allowing for flexibility in terms of annotation sources and their configuration. With JSR-233, this could easily be expanded to allow for configuration in other scripting languages (Jython, Groovy, etc...). The file `config.js` provides an example of configuration.

VCF-based, tab-delimited, or gene-based annotations are added with `vcfAnnotator`, `tsvAnnotator` and `geneAnnotator` respectively. Subsequent modifications/configuration of these annotators can be called as a 'chain'. All public methods in the Java classes for the annotators are available.

Data files will be looked for relative to:
1. The current directory
2. The location of the configuration file
3. The user's home directory (as defined by the operating system).

### vcfAnnotator ###

This is the 'workhorse' annotator for varitas. Arguments to `vcfAnnotator` are the filename of the database file and a comma-separated string regarding which portions of the `INFO` field of the VCF file to include in the output. Because there is no central source of VCF INFO keys, there can be a clash of keys from different data sources. Varitas allows for remapping of keys by specifying 'from=to' for an INFO key name.

For example, for Thousand Genomes project data:

    vcfAnnotator("data/ALL.wgs.phase1_release_v3.20101123.snps_indels_sv.sites.vcf.gz",
    "AMR_AF,ASN_AF,AFR_AF,EUR_AF,AF=TGAF")

#### Additional (chained) configuration

* `setCopyID`: copy the ID field from the reference dataset to the
  ID field of the output. Example:

    vcfAnnotator("data/ncbiSNP137.vcf.gz", "SCS,GMAF,PM,G5A,G5").setCopyID(true)

* `setRequirePass`: only annotate when the variant in the reference
  PASSed all filters in the VCF file

The AF key gets remapped to TGAF (Thousand Genomes Allele frequency).

### tsvAnnotator ###

This is used for dbNSFP, and can also be used for custom data sources that you do not want to put into a VCF format. Arguments to `tsvAnnotator` are the reference data file (bgzip-compressed and tabix indexed) and a comma-separated list of columns to include in the output. I configure dbNSFP as follows:

    tsvAnnotator("data/dbNSFP2.0b3_variant.gz",
      "22=SIFT,23=HDIV_SCORE,24=HDIV_PRED,25=HVAR_SCORE,26=HVAR_PRED," +
      "27=LRT_SCORE,28=LRT_PRED,29=MT_SCORE,30=MT_PRED,31=GERP_NR,32=GERP_RS," +
      "33=PHYLOP,36=LRT_OMEGA").useHeader(true).checkRef(3).checkAlt(4)

For each column the column number (counted starting from 1) must be specified, followed by the '=' character, followed by the `INFO` key to be used for the output.

#### Additional (chained) configuration

* `checkRef` / `checkAlt`: specify a column for the reference
  and variant allele for a site, and only annotate at a site if
  the ref and alt alleles match in the query and reference files
* `useHeader`: use to indicate that the first line of the file is
  a header line. Varitas can use this line to give more information
  in the header of the output VCF file.

### geneAnnotator ###

Adds an annotation based on whether the VCF `INFO` field `Gene_name` in the input file matches a reference file. Arguments are:

1. A tag to be used as the output `INFO` key
2. The reference data file

A simple example:

    geneAnnotator("in_cosmic", "data/cosmicgenes")

The file `cosmicgenes` just contains a list of genes present in cosmic

#### Additional (chained) configuration

If the reference file is more than just a list of genes (if it is a tab-separated file) then more information can be included in the output:

    geneAnnotator("hgnclinks", "data/hgnc.txt").hasHeader(true).setKeyColumn(2).
      setOutputColumns("3=HGNC_NAME")


Filtering
---------

### Filter files ###

Filter configuration, like annotation configuration, is performed with javascript files. The `-f` option takes as its argument
a script containing a `filter` function. Variants will be passed to this function one-by-one. If the variant is returned by
the function, then it is considered to have passed the filter. If the variant fails the filtering step, then the function
must return `null`. All of the public methods of the `VCFVariant` object are available to the script.

As an example, to filter for variants that `PASS` within the input VCF file, you could use the following:

    function filter(v) {
        if ("PASS".equals(v.getFilter())) {
            return v
        } else {
            return null
        }
    }

The fact that "variant or null" instead of "true or false" is used to indicate the result of a filtering operation means that
you could make changes to the variant as you process it. You can decide for yourself whether you think this is a good idea.

### Command-line boolean javascript filters ###

If you want to quickly apply a filter without going through the process of making a separate file, then the `-j` option is
the one for you. The string following this will be executed for each variant, providinging the variable `variant`. The result
of the expression is interpreted as a boolean value to determine whether the variant passes filtering. For example:

    java -jar varitas.jar -i input.vcf -j '"PASS".equals(variant.getFilter())'

This will perform the same filtering as giving a file with the filter function as above. Of course, you could also just perform
this specific filter with `grep` or `awk`, but more interesting filters as left as an exercise to the reader.


Database preparation:
---------------------

### Automatic ###

This part is not all that portable (yet). The `data` directory contains a `Makefile` which can be used to download and prepare some useful data sets. Requirements include `tabix`, `bgzip`, `zip`, `bzcat`, some standard GNU utilities, and of course `make`.

I have tested on Ubuntu and CentOS, but this should also work in other version of Linux and possibly MacOS X (provided the prerequisites are installed).

If all the prerequisites are installed, just move to the data directory and type:

    make

### Manual ###

Using information from a the info fields of a VCF file is easiest: the file just needs to be compressed and indexed:

    bgzip reference.vcf # makes the file reference.vcf.gz
    tabix -p vcf reference.vcf.gz

Similarly, a tab-delimited text file can be processed with `bgzip` and `tabix` (but you need to provide the sequence (chromosome) and position columns to `tabix`).
