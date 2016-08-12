# Finch GraphQL support

Some simple wrappers around [Sangria](http://sangria-graphql.org) to support its use in [Finch](https://github.com/finagle/finch).

In response to a request on the Sangria gitter channel, this is almost directly pulled from a codebase, no effort was made to make it resusable, sorry. It might not compile without some changes.

It is a small layer, that is reasonably opininated, which may not be to your liking. In particular:

* We only support the previous version of Finch, basically because the latest version doesn't yet support mixed content types in endpoints. It will probably work in the latest (we have a branch that does, so with a little work it should be ok).
* We transport GraphQL queries as JSON, over HTTP. This necessitates some nasties from time to time.
* We use Twitter classes instead of the standard library, for things like `Future` and `Try`.
* We use `Future`s containing `Option`s or `Xor`s instead a failing `Future`. Failing `Future`s are only used for things that we'd not reasonably expect a client to be able to handle (i.e. something catastrophic).
* We assume that you want to return some reasonable status codes for errors, rather than `200`s containing the `errors` fields. This may hurt some clients.
* We handle variables in the form of a JSON encoded string (for example from GraphiQL), as well as a straight JSON object.
* We expect that you want strong types for things.

There are some things that need improvement, including:

* Having the variables encoded as `Json` is a bit crap, but it's a quick way to get things up & running, as there is built in support for JSON using a Circe `sangria.marshalling.InputUnmarshaller`.
* In the same vein, the executor returns `Json`, mainly because of the `CirceResultMarshaller`. Ideally both of these would use some form of class that represented the variables/results, and defined an `InputUnmarshaller` and a `ResultMarshaller` for them respectively. In particular, this leads to the unpleasantness with the re-parsing of the JSON returned from the underlying executor to find the status of the result.
* Error handling isn't ideal, we don't return the full details of Sangria's errors as we attempt to decode them in order to detect the cause & return the correct status code.
* We don't do a good job of handling (non-validation) Sangria errors that are the result of client failures (bad queries), or the result of executing the query, e.g. from a downstream service. Both currently return `500`.

# Setup

You will need to add something like the following to your `build.sbt`:

```
// We use the latest 0.5.0 version of Circe as it pulls in the Cats 0.6.0 (which Mouse depends on).
lazy val circeVersion = "0.5.0-M1"
lazy val catsVersion = "0.6.0"
lazy val finagleVersion = "6.35.0"
lazy val finchVersion = "0.11.0-SNAPSHOT"
lazy val sangriaVersion = "0.7.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.github.benhutchison" %% "mouse" % "0.2",

  "com.github.finagle" %% "finch-core" % finchVersion,
  "com.github.finagle" %% "finch-circe" % finchVersion,
  "org.sangria-graphql" %% "sangria" % sangriaVersion,
  "org.sangria-graphql" %% "sangria-relay" % sangriaVersion,
  // depends on 0.4.1 version of Circe, which we don't want)
  "org.sangria-graphql" %% "sangria-circe" % "0.4.4" excludeAll ExclusionRule(organization = "io.circe")
)
```

The classes rely on conversions between Twitter & Scala classes, you can use bijections for this, or roll your own, see [misc below](#misc) for some sample code.

# Usage

1. Configure the executor:

    ```scala
    val schema = ...
    val context = ...
    val executor = GraphQlQueryExecutor.executor(schema, context, maxQueryDepth = 9)
    ```

  Set the max depth to whatever suits your schema.

1. Create a Finch `DecodeRequest` instance (`Decode` in latest Finch) for our query:

    ```scala
    implicit val graphQlQueryDecodeRequest: DecodeRequest[GraphQlQuery] =
        RequestOps.decodeRootJson[GraphQlQuery](queryDecoder, cleanJson)
    ```

1. Write your endpoint:

    ```scala
    object GraphQlApi {
      def graphQlGet: Endpoint[GraphQlResult] =
        get("graphql" :: graphqlQuery) { query: GraphQlQuery =>
          executeQuery(query)
        } handle {
          case e => errorOutput(e)
        }

      def graphQlPost: Endpoint[GraphQlResult] =
        post("graphql" :: body.as[GraphQlQuery]) { query: GraphQlQuery =>
          executeQuery(query)
        } handle {
          case e => errorOutput(e)
        }

      private def executeQuery(query: GraphQlQuery): Future[Output[GraphQlResult]] = {
        val result = executor.execute(query)(globalAsyncExecutionContext)
        result.map(r => r.fold(e => BadRequest(e), v => Ok(v)))
      }

      private def errorOutput(e: Throwable): Output[Nothing] =
        InternalServerError(graphQlError("Error processing GraphQL query", e))
    }
    ```

# GraphiQL

If you want to integrate [GraphiQL](https://github.com/graphql/graphiql) (you should), it's pretty easy.

1. Pull down the [GraphiQL file](https://github.com/graphql/graphiql/blob/master/example/index.html).

1. Stick it somewhere in your classpath.

1. Write an endpoint for it:

```scala
def classpathResource(name: String): Option[InputStream] = Option(getClass.getResourceAsStream(name))

val graphiQlPath = "/graphiql.html"

def graphiql: Endpoint[AsyncStream[Buf]] =
  get("graphiql") {
    classpathResource(graphiQlPath).map(fromStream) match {
      case Some(g) => Ok(AsyncStream.fromReader(g, chunkSize = 512.kilobytes.inBytes.toInt))
      case None => InternalServerError(graphQlError(s"Unable to find GraphiQL at '$graphiQlPath'"))
    }
  }
```

# Other bits

We've added some other bits & pieces to make using Sangria easier.

## Scalar types

There are various helpers that can help you define Scalar types. For example to add support for a tagged type:

```scala
//
// Set up a tagged type
//

import shapeless.tag
import shapeless.tag._

trait PixelWidthTag
type PixelWidth = Int @@ PixelWidthTag
def PixelWidth(w: Int): @@[Int, PixelWidthTag] = tag[PixelWidthTag](w)

//
// Define your GraphQL type for the tagged type
//

private val widthRange = 1 to 4096
private implicit val widthInput = new ScalarToInput[PixelWidth]

private case object WidthCoercionViolation
    extends ValueCoercionViolation(s"Width in pixels, between ${widthRange.start} and ${widthRange.end}")

private def parseWidth(i: Int) = intValueFromInt(i, widthRange, PixelWidth, () => WidthCoercionViolation)

val WidthType = intScalarType(
  "width",
  s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).",
  parseWidth, () => WidthCoercionViolation)

val WidthArg = Argument("imageWidth",
  OptionInputType(WidthType),
  s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).", DefaultImageWidth)
```

## Input types

We've also added support for input types, in a similar way to how other types are handled, they are typesafe.

```scala
// Tagged type
trait PushNotificationTokenTag
type PushNotificationToken = String @@ PushNotificationTokenTag
def PushNotificationToken(t: String): @@[String, PushNotificationTokenTag] = tag[PushNotificationTokenTag](t)

// GraphQL type
private case object PushNotificationTokenCoercionViolation
    extends ValueCoercionViolation(s"Push notification token expected")

private def parseToken(s: String): Either[PushNotificationTokenCoercionViolation.type, PushNotificationToken] =
  Right(PushNotificationToken(s))

val PushNotificationTokenType =
  stringScalarType(
    "PushNotificationToken", s"An iOS push notification token.",
    parseToken, () => PushNotificationTokenCoercionViolation
  )

val PushNotificationTokenArg =
  Argument("token", PushNotificationTokenType, description = s"An iOS push notification token.")

//
// Input type for our type
//
val FieldPushNotificationToken = InputField("token", PushNotificationTokenType, "The push notification token of the device.")

val RegisterDeviceType: InputObjectType[DefaultInput] =
  InputObjectType(
    name = "RegisterDevice",
    description = "Register device fields.",
    fields = List(FieldPushNotificationToken)
  )

val RegisterDeviceArg = Argument(InputFieldName, RegisterDeviceType, "Register device fields.")

//
// Let's use that type in a mutation
//

def registerDevice(ctx: Context[RootContext, Unit]): Action[RootContext, RegisteredDevice] = {
  val maybeDevice = for {
    // Yes! This is typesafe and does What You'd Expect™
    token <- ctx.inputArg(FieldPushNotificationToken)
  } yield Device(token)
  maybeDevice.map(d => ctx.ctx.registerDevice(d)).toScalaTry(graphQlError("Unable to parse input fields"))
}

val MutationType = ObjectType(
  "MutationAPI",
  fields[RootContext, Unit](
    Field("registerDevice", OptionType(RegisteredDeviceType),
      arguments = List(RegisterDeviceArg),
      resolve = ctx => registerDevice(ctx)
    )
  )
)
```

# Misc

## Decoding bits

This class contains utility methods for parsing & decoding, mainly there to ensue consistency around the methods used for this.

```scala
package com.redbubble.util.json

import cats.data.Xor
import io.circe._

trait JsonCodecOps {
  val emptyJsonObject: Json = Json.fromJsonObject(JsonObject.empty)

  final def parse(input: String): ParsingFailure Xor Json = io.circe.jawn.parse(input)

  final def decode[A](input: String)(implicit decoder: Decoder[A]): Error Xor A = io.circe.jawn.decode(input)(decoder)
}

object JsonCodecOps extends JsonCodecOps
```

This class decodes incoming request payloads.

```scala
package com.redbubble.util.http

import com.redbubble.util.error.Errors.jsonDecodeFailedError
import com.redbubble.util.json.JsonCodecOps
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.finch.DecodeRequest

trait RequestOps {
  type JsonCleaner = (String) => String

  /**
    * Decodes a payload, where the data to be decoded sits as an object inside a top level `data` field of the
    * request body. For example: `{ "data" : { ... } }`.
    */
  final def decodeDataJson[A](d: Decoder[A], c: JsonCleaner = identity): DecodeRequest[A] =
    DecodeRequest.instance(payload => decodePayload(c(payload), dataFieldObjectDecoder(d)))

  /**
    * Decodes a payload, where the data to be decoded is an object at the root level of the request body. For
    * example: `{ ... }`.
    */
  final def decodeRootJson[A](d: Decoder[A], c: JsonCleaner = identity): DecodeRequest[A] =
    DecodeRequest.instance(payload => decodePayload(c(payload), rootObjectDecoder(d)))

  private def decodePayload[A](payload: String, decoder: Decoder[A]): Try[A] = {
    val decodedPayload = JsonCodecOps.decode(payload)(decoder)
    decodedPayload.fold(
      error => Throw(jsonDecodeFailedError(s"Unable to decode JSON payload: ${error.getMessage}", error)),
      value => Return(value)
    )
  }

  private def dataFieldObjectDecoder[A](implicit d: Decoder[A]): Decoder[A] = Decoder.instance(c => c.downField("data").as[A](d))

  private def rootObjectDecoder[A](implicit d: Decoder[A]): Decoder[A] = Decoder.instance(c => c.as[A](d))
}

object RequestOps extends RequestOps
```


## Twitter -> Scala Conversions

Conversion code. You can also use [Bijections](https://github.com/twitter/bijection). Meh.

```scala
package com.redbubble.util.async

import com.twitter.util.{Return, Throw, Future => TwitterFuture, Promise => TwitterPromise}

import scala.concurrent.{ExecutionContext, Future => ScalaFuture, Promise => ScalaPromise}
import scala.language.reflectiveCalls
import scala.util.{Failure, Success}

trait FutureOps {
  final def scalaToTwitterFuture[A](f: ScalaFuture[A])(implicit ec: ExecutionContext): TwitterFuture[A] = {
    val p = new TwitterPromise[A]()
    f.onComplete {
      case Success(value) => p.setValue(value)
      case Failure(exception) => p.setException(exception)
    }
    p
  }

  final def twitterToScalaFuture[A](f: TwitterFuture[A]): ScalaFuture[A] = {
    val p = ScalaPromise[A]()
    f.respond {
      case Return(value) => {
        p.success(value)
        ()
      }
      case Throw(exception) => {
        p.failure(exception)
        ()
      }
    }
    p.future
  }
}

object FutureOps extends FutureOps
```

Syntax for conversions.

```scala
package com.redbubble.util.async

package object syntax {

  implicit final class TwitterToScalaFuture[A](val f: TwitterFuture[A]) extends AnyVal {
    def asScala: ScalaFuture[A] = FutureOps.twitterToScalaFuture(f)
  }

  implicit final class ScalaToTwitterFuture[A](val f: ScalaFuture[A]) extends AnyVal {
    def asTwitter(implicit ec: ExecutionContext): TwitterFuture[A] = FutureOps.scalaToTwitterFuture(f)(ec)
  }

}
```
