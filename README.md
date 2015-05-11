Akka - Reusable functionality
=============================
Akka reusable functionality originally written for Registered Traveller UK

Project built with the following (main) technologies:

- Scala

- SBT

- Akka

Introduction
------------
TODO

Build and Deploy
----------------
The project is built with SBT (using Activator on top).

To compile:
> activator compile

To run the specs:
> activator test

To run integration specs:
> activator it:test

To actually run the application, first "assemble" it:
> activator assembly

This packages up an executable JAR - Note that "assembly" will first compile and test.

Then just run as any executable JAR, with any extra Java options for overriding configurations.

For example, to use a config file (other than the default application.conf) which is located on the file system (in this case in the boot directory)
> java -Dconfig.file=test-classes/my-application.conf -jar <jar name>.jar

And other examples:

booting from project root:
> java -Dspray.can.server.port=8080 -jar target/scala-2.11/<jar name>.jar

and running from directory of the executable JAR using a config that is within said JAR:
> java -Dconfig.resource=application.uat.conf -jar <jar name>.jar

Finally you can perform a quick test of the application by calling the API e.g. making a cURL call to the application:
> curl http://localhost:9100/app-name 

Configuration
-------------
TODO

Publishing
----------
To publish the jar to artifactory you will need to 

1. Copy the .credentials file into your <home directory>/.ivy2/
2. Edit this .credentials file to fill in the artifactory security credentials (amend the realm name and host where necessary)

SBT - Revolver
--------------
sbt-revolver is a plugin for SBT enabling a super-fast development turnaround for your Scala applications:

See https://github.com/spray/sbt-revolver

For development, you can use ~re-start to go into "triggered restart" mode.
Your application starts up and SBT watches for changes in your source (or resource) files.
If a change is detected SBT recompiles the required classes and sbt-revolver automatically restarts your application. 
When you press &lt;ENTER&gt; SBT leaves "triggered restart" and returns to the normal prompt keeping your application running.

Example Usage
-------------
```scala
  class SchedulerSpec extends Specification with NoTimeConversions {
    "Actor" should {
      "be scheduled to act as a poller" in new ActorSystemContext {
        val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor), "exampleSchedulerActor")
        exampleSchedulerActor ! Scheduled
        expectMsg(Scheduled)
      }
  
      "not be scheduled to act as a poller" in new ActorSystemContext {
        val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor with NoScheduler), "exampleNoSchedulerActor")
        exampleSchedulerActor ! Scheduled
        expectMsg(NotScheduled)
      }
    }
  }
  
  class ExampleSchedulerActor extends Actor with Scheduler {
    val schedule: Option[Cancellable] = Some(context.system.scheduler.schedule(initialDelay = 1 second, interval = 5 seconds, receiver = self, message = Wakeup))
  
    def receive = LoggingReceive {
      case Wakeup => println("Hello World!")
    }
  }
  
  trait NoScheduler {
    this: Scheduler =>
  
    override val schedule: Option[Cancellable] = None
  }
```