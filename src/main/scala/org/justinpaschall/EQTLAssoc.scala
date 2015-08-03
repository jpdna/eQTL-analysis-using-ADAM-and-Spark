package org.justinpaschall

///////////////////////////////////////////////////////////////////////////////////////////////////
// EQTLAssoc.scala
// Purpose: This demonstration program performs linear regression of genotype vs.
// gene expression phenotypes (eQTLs), making use of ADAM Parquet Genotype file format as input, and Spark
// as a scalable engine to execute hundreds of millions of statistical tests - making use of multiple processors
// and scaling linearly across a cluster such as amazon EC2
//
//  Example Usage:
//  Note: Run from inside the base directory of this cloned repository or adjust the path of
//  /data/RNASEQ60_array_rep_expr.txt in code below
//  Assumes spark-submit is in your path
//
//  Spark_eQTL_assoc/./spark-submit  \
//     --class "org.justinpaschall.EQTLAssoc" \
//     --master local[*]   \
//     --driver-memory 4G \
//     --executor-memory 4G \
//     ../target/uberScalajar-Spark_eQTL_assoc-0.1-SNAPSHOT.jar data/probes1000 \
//       data/chr2vcf.adam 8 TEST0_jplap_run_chr2vcfadam_probes1000
//
// Author: Justin Paschall
// 8-3-2015
////////////////////////////////////////////////////////////////////////////////////////////////////

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext.rddToPairRDDFunctions
import org.apache.spark.rdd.RDD

import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{Genotype, GenotypeAllele}

import org.apache.commons.math3.stat.regression.SimpleRegression
import java.util.Calendar

object EQTLAssoc {

  def main(args: Array[String]): Unit = {

    val startTime = Calendar.getInstance().getTime()
    println("Starting: " + startTime)

    /////////////////////////////////////////////////////
    // Command line parameters
    val probe_list = args(0)
    val vcf_data_file = args(1)
    val num_partitions = args(2)
    val output_file = args(3)
    println("Command line parameters: ")
    println("probe_list: " + probe_list)
    println("vcf_data_file: " + vcf_data_file)
    println("num_partitions: : " + num_partitions)
    println("output_file: " + output_file)
    //////////////////////////////////////////////////////

    // Setup Spark
    val conf = new SparkConf()
      .setAppName("eQTL linear regression App using ADAM formats")
    val sc = new SparkContext(conf)

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Prepare Spark broadcast value phenoMapBroadcast containing a Map
    //  ( "sampleid-phenoypeid" -> GeneExpressionFloatValue )
    // with composite key of "Sample-id-phenotype_id" where the value is the gene expression for a given
    // gene in an individual person's celline sample
    // This gene expression value needs to be available to all executors for all variants against all phenotypes analysis

    val data1 = scala.io.Source.fromFile("data/RNASEQ60_array_rep_expr.txt").getLines()

    val headerArray = data1.next.split("\t")
    def makeGeneExprRowMap (x: String) = {
      val myLinkArray = x.split("\t").toArray;
      val myMutableMap = collection.mutable.Map[String,Float]()
      val rowPreMap  = for(i <- 4 to (headerArray.length - 1)) {
        val localString = headerArray(i) + "-" + myLinkArray(0);
        val localFloat = myLinkArray(i).toFloat
        myMutableMap(localString) = localFloat
      }
      myMutableMap
    }
    val sampleGeneToExprMap = data1.flatMap(makeGeneExprRowMap).toMap

    // This hack of copying to phenomap_mut using foreach below appeared to be necessary to break link to the
    // file input buffer and allow this phenomap_mut object to be serialized as a Spark broadcast
    val phenomap_mut = collection.mutable.Map[String,Float]()
    sampleGeneToExprMap.foreach{ case (x,y) => phenomap_mut(x.toString)=y}

    // Create the Spark Broadcast
    val phenoMapBroadcast = sc.broadcast(phenomap_mut)
    // phenoMapBroadcast now set
    ////////////////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    // This following code loads a Set of phenotypes to be analyzed,
    // In future to be converted to a Map associating a phenotype to genomic position (eg. cis acting only)  or
    // based on other criteria such as metabolic network in order to select
    // specific phenotypes for analysis with given set of variants
    val data2 = scala.io.Source.fromFile(probe_list).getLines()

    val phenoSet_load = data2.toSet
    val phenoSet_mut = collection.mutable.Set[String]()
    phenoSet_load.foreach{ case (x) => phenoSet_mut += x.toString }

    println("Log: About to load phenoListBroadcast")
    val phenoSetBroadcast = sc.broadcast(phenoSet_mut)
    println("Log: Done loading phenoListBroadcast")
    ///////////////////////////////////////////////////////////////////////////////////////////////////////


    // Load Genotype RDD from from file, can be VCF or filename.adam Parquet format
    val adamVariants: RDD[Genotype] = sc.loadGenotypes(vcf_data_file)

    // Parse out useful values from the Genotype RDD from each record
    // inspired by alleleCount function in ADAM
    val usefulData = adamVariants.map(p => (
      p.getVariant.getContig.getContigName,
      p.getVariant.getStart,
      p.getVariant.getReferenceAllele,
      p.getVariant.getAlternateAllele,
      p.getAlleles.get(0),
      p.getAlleles.get(1),
      p.getSampleId,
      p.getSplitFromMultiAllelic
      )).filter(_._8 == false).repartition(num_partitions.toInt)


    // returns the count of Alt alleles present in a given genotype
    def countAltAllelePerSample ( x: (String, java.lang.Long, String, String,
                                      org.bdgenomics.formats.avro.GenotypeAllele,
                                      org.bdgenomics.formats.avro.GenotypeAllele, String, java.lang.Boolean)) = {
       val (chr,pos,ref,alt,allele1,allele2,sampleid,isSplitFromMultiAllelic) = x;
       val chr_pos_varallele_key: String = chr + "_" + pos + "_" + alt;
       val altCount = (allele1,allele2) match {
        case (org.bdgenomics.formats.avro.GenotypeAllele.Ref,org.bdgenomics.formats.avro.GenotypeAllele.Ref) => 0
        case (org.bdgenomics.formats.avro.GenotypeAllele.Ref,org.bdgenomics.formats.avro.GenotypeAllele.Alt) => 1
        case (org.bdgenomics.formats.avro.GenotypeAllele.Alt,org.bdgenomics.formats.avro.GenotypeAllele.Ref) => 1
        case (org.bdgenomics.formats.avro.GenotypeAllele.Alt,org.bdgenomics.formats.avro.GenotypeAllele.Alt) => 2
      }

      // return this "alleles" tuple representing a variant key and (sample_id,altCount) value
      ( chr_pos_varallele_key, (sampleid, altCount) )
    }

    // function runVariantPhenoRegression receives as its argument x
    // which is a tuple of type  (String, Iterable[(String,Int)]))
    // x._1 is a composite key: chr_pos_varallele_key returned as the first member in "alleles" tuple returned from
    // function countAltAlleleperSample
    // x._2 is an Iterable of tuple (sample,alt_count) generated by GroupBy of the key from x._1
    // Thus, each argument x passed in represents a variant and the vector of alleleCounts
    // for each sample for that x._1 variant
    def runVariantPhenoRegression (x: (String, Iterable[(String,Int)])) = {
      val (myKey, sampleIdAlleleCountTuple) = x

      // Helper function performing the linear regression using the apache commons
      // linear regression object SimpleRegression
      def refRegressionPerPheno(currPheno: String) = {
        var myRegression = new SimpleRegression()
        for( a <- sampleIdAlleleCountTuple ) {
          val (currSampleID,currAltCount) = a
          val phenokey = currSampleID + "-" + currPheno
          myRegression.addData(currAltCount, phenoMapBroadcast.value(phenokey) )
        }

        // returns tuple3 below, including myRegression: SimpleRegression
        (myKey, currPheno, myRegression: SimpleRegression)
      }
      val phenoList = phenoSetBroadcast.value

      //Note: this map below is normal scala map over the Broadcast variable PhenoSetBroadCast
      // to map over all the phenotpyes, it is NOT a RDD map
      // Returns the tuple (x._1, x._2 .....)   generated by the map taking function refRegressionPerPheno
      phenoList.map(refRegressionPerPheno).map( x => (x._1, x._2, x._3.getSignificance(), x._3.getRSquare) )
        .filter(_._3 < 0.001)
    }

    val alleleCountsAtomic = usefulData.map(countAltAllelePerSample)
    val alleleCountsGroupedByAllele = alleleCountsAtomic.groupByKey()

    val assocmap = alleleCountsGroupedByAllele.flatMap(runVariantPhenoRegression)

    // Write the association results to file
    // Results are in the form of tuple (chr_pos_allele,geneExpressionProbeName,Pvalue,Rquared)
    assocmap.saveAsTextFile(output_file)

    val endTime =  Calendar.getInstance().getTime()
    println("Finished: " + endTime)
    val elapsedTimeMinutes = (startTime.getTime() - endTime.getTime()) / 1000.0 / 60.0
    println("Elapsed time in minutes" + elapsedTimeMinutes)

  }
}
