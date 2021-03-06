/**
The following program can be compiled and run using SBT
Wrapper scripts have been provided with this
The following script can be run to compile the code
./compile.sh

The following script can be used to run this application in Spark
./submit.sh com.packtpub.sfb.ConnectionApp
**/

package com.packtpub.sfb
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
import org.apache.spark.sql.{Row, SparkSession}

object ConnectionApp {
	// Create the Spark Session and the spark context			
	val spark = SparkSession
			.builder
			.appName(getClass.getSimpleName)
			.getOrCreate()
	val sc = spark.sparkContext	
  //TODO: Change the following directory to point to your data directory
  val dataDir = "/Users/RajT/Documents/Writing/SparkForBeginners/To-PACKTPUB/Contents/B05289-09-DesigningSparkApplications/Code/Data/"
  import spark.implicits._
  //Define the case classes in Scala for the entities
  case class User(Id: Long, UserName: String, FirstName: String, LastName: String, EMail: String, AlternateEmail: String, Phone: String)
  case class Follow(Follower: String, Followed: String)
  case class ConnectedUser(CCId: Long, UserName: String)
  //Define the utility functions that are to be passed in the applications
  def toUser =  (line: Seq[String]) => User(line(0).toLong, line(1), line(2),line(3), line(4), line(5), line(6))
  def toFollow =  (line: Seq[String]) => Follow(line(0), line(1))
  
  def main(args: Array[String]) {
	  checkWhetherConnected()
  }
  
  def checkWhetherConnected(){
	//Load the user data into an RDD
	val userDataRDD = sc.textFile(dataDir + "user.txt").map(_.split("\\|")).map(toUser(_))
	//Convert the RDD into data frame
	val userDataDF = userDataRDD.toDF()
	userDataDF.createOrReplaceTempView("user")
	userDataDF.show()
	//Load the follower data into an RDD
	val followerDataRDD = sc.textFile(dataDir + "follower.txt").map(_.split("\\|")).map(toFollow(_))
	//Convert the RDD into data frame
	val followerDataDF = followerDataRDD.toDF()
	followerDataDF.createOrReplaceTempView("follow")
	followerDataDF.show()
	//By joining with the follower and followee users with the master user data frame for extracting the unique ids
	val fullFollowerDetails = spark.sql("SELECT b.Id as FollowerId, c.Id as FollowedId, a.Follower, a.Followed FROM follow a, user b, user c WHERE a.Follower = b.UserName AND a.Followed = c.UserName")
	fullFollowerDetails.show()
	//Create the vertices of the connections graph
	val userVertices: RDD[(Long, String)] = userDataRDD.map(user => (user.Id, user.UserName))
	userVertices.foreach(println)
	//Create the edges of the connections graph 
	val connections: RDD[Edge[String]] = fullFollowerDetails.rdd.map(conn => Edge(conn.getAs[Long]("FollowerId"), conn.getAs[Long]("FollowedId"), "Follows"))
	connections.foreach(println)
	//Create the graph using the vertices and the edges
	val connectionGraph = Graph(userVertices, connections)
	//Calculate the connected users
  	val cc = connectionGraph.connectedComponents()
  	// Extract the triplets of the connected users
  	val ccTriplets = cc.triplets
  	// Print the structure of the triplets
  	ccTriplets.foreach(println)
  	//Print the vertex numbers and the corresponding connected component id. The connected component id is generated by the system and it is to be taken only as a unique identifier for the connected component
  	val ccProperties = ccTriplets.map(triplet => "Vertex " + triplet.srcId + " and " + triplet.dstId + " are part of the CC with id " + triplet.srcAttr)
  	ccProperties.foreach(println)
  	//Find the users in the source vertex with their CC id
  	val srcUsersAndTheirCC = ccTriplets.map(triplet => (triplet.srcId, triplet.srcAttr))
  	//Find the users in the destination vertex with their CC id
  	val dstUsersAndTheirCC = ccTriplets.map(triplet => (triplet.dstId, triplet.dstAttr))
  	//Find the union
  	val usersAndTheirCC = srcUsersAndTheirCC.union(dstUsersAndTheirCC)
  	//Join with the name of the users
	//Convert the RDD to DataFrame
  	val usersAndTheirCCWithName = usersAndTheirCC.join(userVertices).map{case (userId,(ccId,userName)) => (ccId, userName)}.distinct.sortByKey().map{case (ccId,userName) => ConnectedUser(ccId, userName)}.toDF()
	usersAndTheirCCWithName.createOrReplaceTempView("connecteduser")
	val usersAndTheirCCWithDetails = spark.sql("SELECT a.CCId, a.UserName, b.FirstName, b.LastName FROM connecteduser a, user b WHERE a.UserName = b.UserName ORDER BY CCId")
  	//Print the user names with their CC component id. If two users share the same CC id, then they are connected
  	usersAndTheirCCWithDetails.show()
  }
}




