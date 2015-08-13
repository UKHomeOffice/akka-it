package akka

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import uk.gov.homeoffice.configuration.ConfigFactorySupport

case object Scheduled

case object NotScheduled

case object Wakeup

trait Scheduler extends ActorLogging with ConfigFactorySupport {
  this: Actor =>

  private var cancellable: Cancellable = _

  val schedule: Cancellable

  def schedule(initialDelay: Duration = 0 seconds, interval: Duration, receiver: ActorRef = self, message: Any = Wakeup) =
    context.system.scheduler.schedule(initialDelay, interval, receiver, message)

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = if (cancellable != null) cancellable.cancel()

  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case Scheduled =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! (if (cancellable == null) NotScheduled else if (cancellable.isCancelled) NotScheduled else Scheduled)

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}

/**
 * Why provide a "no schedule" such that a scheduled Actor will not periodically "wake up" and perform its duties?
 * One way that this trait can be very useful, is when testing a scheduled Actor's functionality - essentially its API (which is only exposed via a message protocol).
 * When testing a scheduled actor's functionality, messages sent to it by a specification can be processed and assertions made on the outcome of said processing.
 * During a test, you would usually not want the actor to also be "woken up" by the scheduling mechanism, as this would probably interfere with the running test.
 */
trait NoSchedule {
  this: Scheduler =>

  override lazy val schedule: Cancellable = new Cancellable {
    def isCancelled = true

    def cancel() = true
  }
}