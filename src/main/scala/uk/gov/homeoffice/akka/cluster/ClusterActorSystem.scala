package uk.gov.homeoffice.akka.cluster

import scala.collection.JavaConversions._
import scala.util.Try
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.configuration.ConfigFactorySupport

/**
  * Create an Actor System that can be clustered by enhancing an implicit application Config i.e. a config that is in scope of this object, which by default will come from ConfigFactory.load
  *
  * For a module to be clustered, seed-nodes have to be configured, and one of the entries in seed-nodes will be the module that wishes to be part of the cluster.
  * Any number of nodes in a cluster is stipulated in seed-nodes of your configuration such as application.conf.
  * When a module is run on a node, it is represented as one entry in seed-nodes, and we have to state which node in seed-nodes the module is.
  * E.g. if the module is to be run on node 1, represented by the first entry in seed-nodes, then we must state that this is indeed node 1.
  * This can be done by providing the JVM property cluster.node=1
  *
  * The config just has to provide seed nodes of the following format:
  * {{{
  *   akka {
  *     cluster {
  *       seed-nodes = [{
  *         host = "127.0.0.1"
  *         host = \${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *         port = 2661
  *         port = \${?CLUSTER_SEED_NODE_PORT_1}
  *       }, {
  *         host = "127.0.0.1"
  *         host = \${?CLUSTER_SEED_NODE_HOST_2}
  *
  *         port = 2662
  *         port = \${?CLUSTER_SEED_NODE_PORT_2}
  *       }]
  *     }
  *   }
  *
  *   Or maybe
  *
  *   akka {
  *     cluster {
  *       seed-nodes {
  *         1 {
  *           host = "127.0.0.1"
  *           host = \${?CLUSTER_SEED_NODE_HOST_1} # If using environment variables
  *
  *           port = 2661
  *           port = \${?CLUSTER_SEED_NODE_PORT_1}
  *         }
  *
  *         2 {
  *           host = "127.0.0.1"
  *           host = \${?CLUSTER_SEED_NODE_HOST_2}
  *
  *           port = 2662
  *           port = \${?CLUSTER_SEED_NODE_PORT_2}
  *         }
  *       }
  *     }
  *   }
  * }}}
  *
  * Note, that if you do not want the default cluster name of "cluster-actor-system" for this cluster actor system, then provide one e.g.
  * {{{
  *   akka {
  *     cluster {
  *       name = "yourClusterActorSystemName"
  * }}}
  *
  * As an example, to run locally 3 instances of a module in 3 consoles (so there must be 3 corresponding entries in seed-nodes):
  * {{{
  *   sbt '; set javaOptions += "-Dcluster.node=1"; run'
  *   CLUSTER_SEED_NODE_PORT_2=2662 sbt '; set javaOptions += "-Dcluster.node=2"; run'
  *   CLUSTER_SEED_NODE_PORT_3=2663 sbt '; set javaOptions += "-Dcluster.node=3"; run'
  * }}}
  *
  * This example shows how we use environment variables to set specific ports for the second and third nodes.
  * And if necessary, a non seed-node could be started up as:
  * {{{
  *   sbt '; set javaOptions ++= Seq("-Dcluster.host=127.0.0.1", "-Dcluster.port=2665"); run'
  * }}}
  *
  * Or running the assembled JAR:
  * {{{
  *   java -Dcluster.node=1 -jar <jar name>.jar
  *   CLUSTER_SEED_NODE_PORT_2=2662 java -Dcluster.node=2 -jar <jar name>.jar
  *   CLUSTER_SEED_NODE_PORT_3=2663 java -Dcluster.node=3 -jar <jar name>.jar
  * }}}
  */
object ClusterActorSystem {
  implicit private val defaultConfig = ConfigFactory.load

  private val clusterActorSystem = new ClusterActorSystem

  /**
    * Create/Get an Actor System as a seed node of a cluster OR as a "dynamic" node (a non seed node) - where the fallback is assuming this is node 1.
    * Unlike the other "apply" methods of this object, this one assumes you either stipulate the system property:
    * {{{
    *   cluster.node
    *   representing which of the configured seed nodes to use e.g. 1 from a configuration of 3 seed nodes
    *   i.e. the following system property would have to be set:
    *   -Dcluster.node=1
    * }}}
    * or the system properties:
    * {{{
    *   cluster.host and cluster.port
    *   representing a node that is not one of the configured seed nodes e.g.
    *   -Dcluster.host=prod.home-office.gov.uk
    *   -Dcluster.port=2665
    * }}}
    * @return ActorSystem that is part of a cluster
    */
  def apply(): ActorSystem = clusterActorSystem.node

  /**
    * Create/Get an Actor System as a seed node of a cluster.
    * @param node Int Representing the node number of the seed nodes e.g. node 2 of 3 seed nodes.
    *             Client code will probably want this to be given as an environment variable e.g.
    *             -Dcluster.node=1, captured in code as sys.props("cluster.node").toInt
    *             NOTE that we go by node numbers starting from 1 i.e. non-technical index based and so not starting from 0 index.
    * @return ActorSystem that is part of a cluster
    */
  def apply(node: Int): ActorSystem = clusterActorSystem.node(node)

  /**
    * Create/Get an Actor System to dynamically add to cluster seed nodes.
    * @param host String Of the host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.host=rtp.homeoffice.gov.uk, captured in code as sys.props("cluster.host")
    * @param port Int Of the port for the given host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.port=2665, captured in code as sys.props("cluster.port").toInt
    * @return ActorSystem that is part of a cluster
    */
  def apply(host: String, port: Int): ActorSystem = clusterActorSystem.node(host, port)
}

/**
  * Client code should use the ClusterActorSystem object.
  * However, if specific configuration is required i.e. the default use of ConfigFactory is for some reason not flexible enough,
  * client code could extend this class to provide a configuration programmatically.
  * One reason for using this class instead of the ClusterActorSystem would be to have multiple clusters to categorise groups of actors e.g.
  * a specific config with its own cluster name for one set of actors to run on say 3 nodes, and a second set with a config and cluster name to again run on the same 3 nodes.
  * @param config Config to be used that must include seed-nodes as illustrated in the above object's Scaladoc.
  */
protected class ClusterActorSystem(implicit config: Config) extends ConfigFactorySupport with Logging {
  val clusterName = config.text("akka.cluster.name", "cluster-actor-system")

  val seedNodes: Seq[ClusterNode] = {
    val nodes: Seq[String] = clusterSeedNodes
    info(s"Cluster $clusterName configured with seed nodes: ${nodes.mkString(", ")}")

    nodes.zipWithIndex map { case (node, index) =>
      val nodeNumber = index + 1
      val HostPort = """.*?@(.*?):(\d*).*""".r
      val HostPort(host, port) = node
      info(s"Configuring cluster seed node $nodeNumber as $host:$port in cluster '$clusterName'")

      ClusterNode(nodeNumber, host, port.toInt, clusterConfig(host, port.toInt, nodes))
    }
  }

  /**
    * Create/Get an Actor System as a seed node of a cluster OR as a "dynamic" node (a non seed node) - where the fallback is assuming this is node 1.
    * Unlike the other "apply" methods of this object, this one assumes you either stipulate the system property:
    * {{{
    *   cluster.node
    *   representing which of the configured seed nodes to use e.g. 1 from a configuration of 3 seed nodes
    *   i.e. the following system property would have to be set:
    *   -Dcluster.node=1
    * }}}
    * or the system properties:
    * {{{
    *   cluster.host and cluster.port
    *   representing a node that is not one of the configured seed nodes e.g.
    *   -Dcluster.host=prod.home-office.gov.uk
    *   -Dcluster.port=2665
    * }}}
    * @return ActorSystem that is part of a cluster
    */
  def node: ActorSystem = {
    def seedNode: Option[ActorSystem] = sys.props.get("cluster.node") map { n => node(n.toInt) }

    def dynamicNode: Option[ActorSystem] = for {
      host <- sys.props.get("cluster.host")
      port <- sys.props.get("cluster.port")
    } yield node(host, port.toInt)

    def node1: ActorSystem = {
      warn(s"""Incomplete cluster configuration, so will fallback to booting cluster node 1 - Is this what you really want, or are you missing the appropriate cluster system properties "cluster.node" or "cluster.host, cluster.port"?""")
      node(1)
    }

    seedNode orElse dynamicNode getOrElse node1
  }

  /**
    * Create/Get an Actor System as a seed node of a cluster.
    * @param node Int Representing the node number of the seed nodes e.g. node 2 of 3 seed nodes.
    *             Client code will probably want this to be given as an environment variable e.g.
    *             -Dcluster.node=1, captured in code as sys.props("cluster.node").toInt
    *             NOTE that we go by node numbers starting from 1 i.e. non-technical index based and so not starting from 0 index.
    * @return ActorSystem that is part of a cluster
    */
  def node(node: Int): ActorSystem = seedNodes(node - 1).actorSystem

  /**
    * Create/Get an Actor System to dynamically add to cluster seed nodes.
    * @param host String Of the host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.host=rtp.homeoffice.gov.uk, captured in code as sys.props("cluster.host")
    * @param port Int Of the port for the given host to dynamically add to the seed cluster nodes.
    *             Client code will probably want this to be given as an environment variable e.g. -Dcluster.port=2665, captured in code as sys.props("cluster.port").toInt
    * @return ActorSystem that is part of a cluster
    */
  def node(host: String, port: Int): ActorSystem = {
    info(s"""Booting Cluster actor system $host:$port dynamically in cluster "$clusterName"""")
    ActorSystem(clusterName, clusterConfig(host, port, clusterSeedNodes))
  }

  def clusterSeedNodes: Seq[String] = Try {
    config.getList("akka.cluster.seed-nodes") map {
      _.atKey("seed-node")
    } map { seedNode =>
      val host = seedNode.getString("seed-node.host")
      val port = seedNode.getInt("seed-node.port")
      s""""akka.tcp://$clusterName@$host:$port""""
    }
  } getOrElse {
    config getStringList "akka.cluster.seed-nodes" map { s => s""""$s"""" }
  }

  def clusterConfig(host: String, port: Int, seedNodes: Seq[String]): Config = ConfigFactory.parseString(s"""
    akka {
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }

      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        log-remote-lifecycle-events = off

        netty {
          tcp {
            hostname = "$host"
            port = $port
          }
        }
      }

      cluster {
        seed-nodes = [
          ${seedNodes mkString ", "}
        ]

        min-nr-of-members = 2

        metrics {
          enabled = off
        }
      }

      extensions = [
        "akka.cluster.pubsub.DistributedPubSub",
        "akka.cluster.metrics.ClusterMetricsExtension"
      ]
    }""").withFallback(config)

  case class ClusterNode(node: Int, host: String, port: Int, c: Config) {
    lazy val actorSystem = {
      info(s"""Booting Cluster actor system node $node on $host:$port in cluster "$clusterName"""")
      ActorSystem(clusterName, c)
    }
  }
}