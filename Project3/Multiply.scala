package edu.uta.cse6331

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

object Multiply {
  
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Multiply")
    val sc = new SparkContext(conf)
    val m = sc.textFile(args(0)).map( line => { val a = line.split(",")
                                                (a(0).toInt,a(1).toInt,a(2).toDouble) } )
    val n = sc.textFile(args(1)).map( line => { val b = line.split(",")
                                                (b(0).toInt,b(1).toInt,b(2).toDouble)} )
    val res1 = m.map( m => (m._2,m) ).join(n.map( n => (n._1,n) ))
                .map { case (
                    k,(m,n)) => ((m._1,n._2),(m._3*n._3)) }
    
    val res2 = res1.reduceByKey((x,y) => (x+y))
    val res3 = res2.sortByKey(true,0)
    val final_res = res3.collect();
    final_res.foreach(println);
  
    sc.stop()
  }
  
}