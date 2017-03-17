# Finch GraphQL support

Some simple wrappers around [Sangria](http://sangria-graphql.org) to support its use in [Finch](https://github.com/finagle/finch).

In response to a request on the Sangria gitter channel, this is almost directly pulled from a production codebase, no effort was made to make it resusable, sorry. It might not compile without some changes.

It is a small layer, that is reasonably opininated, which may not be to your liking. In particular:

* We transport GraphQL queries as JSON, over HTTP. This necessitates some nasties from time to time.
* We use Twitter classes instead of the standard library, for things like `Future` and `Try`.
* We use `Future`s containing `Option`s or `Either`s instead a failing `Future`. Failing `Future`s are only used for things that we'd not reasonably expect a client to be able to handle (i.e. something catastrophic).
* We handle variables in the form of a JSON encoded string (for example from GraphiQL), as well as a straight JSON object.
* We do our best to give back semi-sane HTTP status codes.
* We expect that you want strong types for things.

There are some things that need improvement, including:

* In the same vein, the executor returns `Json`, mainly because of the `CirceResultMarshaller`. Ideally both of these would use some form of class that represented the variables/results, and defined an `InputUnmarshaller` and a `ResultMarshaller` for them respectively. In particular, this leads to the unpleasantness with the re-parsing of the JSON returned from the underlying executor to find the status of the result.

# Setup

You will need to add something like the following to your `build.sbt`:

```
lazy val finchVersion = "0.13.1"
lazy val sangriaVersion = "1.0.0"
lazy val sangriaCirceVersion = "1.0.1"

lazy val finchCore = "com.github.finagle" %% "finch-core" % finchVersion
lazy val finchCirce = "com.github.finagle" %% "finch-circe" % finchVersion
lazy val sangria = "org.sangria-graphql" %% "sangria" % sangriaVersion
lazy val sangriaRelay = "org.sangria-graphql" %% "sangria-relay" % sangriaVersion
lazy val sangriaCirce = "org.sangria-graphql" %% "sangria-circe" % sangriaCirceVersion

libraryDependencies ++= Seq(
  finchCore,
  finchCirce,
  sangria,
  sangriaRelay,
  sangriaCirce
)
```

The classes rely on conversions between Twitter & Scala classes, you can use bijections for this, or roll your own, see [misc below](#misc) for some sample code.

# Usage

1. Configure the executor:

    ```scala
    val schema = ...
    val context = ...
    val errorReporter = ... // a way to log errors, e.g. Rollbar
    val serverMetrics = ... // a thin wrapper around com.twitter.finagle.stats.StatsReceiver
    val executor = GraphQlQueryExecutor.executor(
      schema, context, maxQueryDepth = 10)(errorReporter, serverMetrics)
    ```

  Set the max depth to whatever suits your schema.

1. Create a Finch `Decode.Json` instance for our query:

    ```scala
	implicit val graphQlQueryDecode: Decode.Json[GraphQlQuery] =
	    RequestOps.decodeRootJson[GraphQlQuery](queryDecoder, cleanJson)
    ```

1. Write your endpoint:

    ```scala
    object GraphQlApi {
      val stats = StatsReceiver.stats

      def graphQlGet: Endpoint[Json] =
        get("graphql" :: graphqlQuery) { query: GraphQlQuery =>
          executeQuery(query)
        }

      def graphQlPost: Endpoint[Json] =
        post("graphql" :: jsonBody[GraphQlQuery]) { query: GraphQlQuery =>
          executeQuery(query)
        }

      private def executeQuery(query: GraphQlQuery): Future[Output[Json]] = {
        val operationName = query.operationName.getOrElse("unnamed_operation")
        stats.counter("count", operationName).incr()
        Stat.timeFuture(stats.stat("execution_time", operationName)) {
          runQuery(query)
        }
      }

      private def runQuery(query: GraphQlQuery): Future[Output[Json]] = {
        val result = executor.execute(query)(globalAsyncExecutionContext)

        // Do our best to map the type of error back to a HTTP status code
        result.map {
          case SuccessfulGraphQlResult(json) => Output.payload(json, Status.Ok)
          case ClientErrorGraphQlResult(json, _) => Output.payload(json, Status.BadRequest)
          case BackendErrorGraphQlResult(json, _) => Output.payload(json, Status.InternalServerError)
        }
      }
    }
    ```

# GraphiQL

If you want to integrate [GraphiQL](https://github.com/graphql/graphiql) (you should), it's pretty easy.

1. Pull down the latest [GraphiQL file](https://github.com/graphql/graphiql/blob/master/example/index.html).

1. You may need to adjust the paths within the GraphiQL file if you're using versioned paths, etc.

1. Stick it somewhere in your classpath.

1. Write an endpoint for it:

    ```scala
    object ExploreApi {
      private val graphiQlPath = "/graphiql.html"
      val exploreApi = explore

      def explore: Endpoint[Response] = get("explore") {
        classpathResource(graphiQlPath).map(fromStream) match {
          case Some(content) => htmlResponse(Status.Ok, AsyncStream.fromReader(content, chunkSize = 512.kilobytes.inBytes.toInt))
          case None => textResponse(Status.InternalServerError, Buf.Utf8(s"Unable to find GraphiQL at '$graphiQlPath'"))
        }
      }

	  private def classpathResource(name: String): Option[InputStream] = Option(getClass.getResourceAsStream(name))
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

private val widthRange = 1 to MaxImageDimension
private implicit val widthInput = new ScalarToInput[PixelWidth]

private case object WidthCoercionViolation
    extends ValueCoercionViolation(s"Width in pixels, between ${widthRange.start} and ${widthRange.end}")

private def parseWidth(i: Int) = intValueFromInt(i, widthRange, PixelWidth, () => WidthCoercionViolation)

val WidthType = intScalarType(
  "width",
  s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).",
  parseWidth, () => WidthCoercionViolation)

val WidthArg: Argument[PixelWidth] = Argument(
  name = "width",
  argumentType = OptionInputType(WidthType),
  description = s"The width of an image, in pixels, between ${widthRange.start} and ${widthRange.end} (default $DefaultImageWidth).", defaultValue = DefaultImageWidth)
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
val FieldPushNotificationToken = InputField(
  "token",
  OptionInputType(PushNotificationTokenType),
  "If available, the push notification token for the device. May be empty if the user has not given permission to send notifications."
)

val RegisterDeviceType: InputObjectType[DefaultInput] =
  InputObjectType(
    name = "RegisterDevice",
    description = "Register device fields.",
    fields = List(FieldPushNotificationToken, FieldBundleId, FieldAppVersion, FieldOsVersion)
  )

val RegisterDeviceArg = Argument(InputFieldName, RegisterDeviceType, "Register device fields.")

//
// Let's use that type in a mutation
//

object DeviceRegistration extends InputHelper {
  def registerDevice(ctx: Context[RootContext, Unit]): Action[RootContext, RegisteredDevice] = {
    val token = ctx.inputArg(FieldPushNotificationToken).flatten
    val registeredDevice = for {
      bundleId <- ctx.inputArg(FieldBundleId)
      appVersion <- ctx.inputArg(FieldAppVersion).flatMap(fromRawVersion)
      osVersion <- ctx.inputArg(FieldOsVersion).flatMap(fromRawVersion)
    } yield {
      val device = Device.device(token, App(bundleId, appVersion), osVersion)
      ctx.ctx.registerDevice(device)
    }
    registeredDevice.getOrElse(Future.exception(graphQlError("Unable to parse device input fields"))).asScala
  }
}

val MutationType: ObjectType[RootContext, Unit] = ObjectType(
  "MutationAPI",
  description = "The Redbubble iOS Mutation API.",
  fields[RootContext, Unit](
    Field(
      name = "registerDevice",
      arguments = List(RegisterDeviceArg),
      fieldType = OptionType(RegisteredDeviceType),
      resolve = registerDevice
    )
  )
)
```

# Misc

## Decoding bits

This class contains utility methods for parsing & decoding, mainly there to ensue consistency around the methods used for this.

```scala
package com.redbubble.util.json

import com.twitter.io.Buf
import com.redbubble.util.io.BufOps._
import io.circe._

trait CodecOps {
  val emptyJsonObject: Json = Json.fromJsonObject(JsonObject.empty)

  /**
    * @note Do not use this for production code, prefer `parse(Buf)`.
    */
  final def parse(input: String): Either[ParsingFailure, Json] = io.circe.jawn.parse(input)

  final def parse(input: Buf): Either[ParsingFailure, Json] =
    io.circe.jawn.parseByteBuffer(bufToByteBuffer(input))

  final def encode[A](a: A)(implicit encoder: Encoder[A]): Json = encoder.apply(a)

  /**
    * @note Do not use this for production code, prefer `parse(Buf)`.
    */
  final def decode[A](input: String)(implicit decoder: Decoder[A]): Either[Error, A] =
    parse(input).flatMap(decoder.decodeJson)

  final def decode[A](input: Buf)(implicit decoder: Decoder[A]): Either[Error, A] =
    parse(input).flatMap(decoder.decodeJson)
}

object CodecOps extends CodecOps
```

This class decodes incoming request payloads.

```scala
package com.redbubble.util.http

import com.redbubble.util.http.Errors.jsonDecodeFailedError
import com.redbubble.util.json.CodecOps
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.finch.Decode

trait RequestOps {
  type JsonCleaner = (Buf) => Buf

  /**
    * Decodes a payload, where the data to be decoded sits as an object inside a top level `data` field of the
    * request body. For example: `{ "data" : { ... } }`.
    */
  final def decodeDataJson[A](d: Decoder[A], c: JsonCleaner = identity): Decode.Json[A] =
    Decode.json((payload, _) => decodePayload(c(payload), dataFieldObjectDecoder(d)))

  /**
    * Decodes a payload, where the data to be decoded is an object at the root level of the request body. For
    * example: `{ ... }`.
    */
  final def decodeRootJson[A](d: Decoder[A], c: JsonCleaner = identity): Decode.Json[A] =
    Decode.json((payload, _) => decodePayload(c(payload), rootObjectDecoder(d)))

  private def decodePayload[A](payload: Buf, decoder: Decoder[A]): Try[A] = {
    val decodedPayload = CodecOps.decode(payload)(decoder)
    decodedPayload.fold(
      error => Throw(jsonDecodeFailedError(s"Unable to decode JSON payload: ${error.getMessage}", error)),
      value => Return(value)
    )
  }

  private def dataFieldObjectDecoder[A](implicit d: Decoder[A]): Decoder[A] =
    Decoder.instance(c => c.downField("data").as[A](d))

  private def rootObjectDecoder[A](implicit d: Decoder[A]): Decoder[A] = Decoder.instance(c => c.as[A](d))
}

object RequestOps extends RequestOps
```

## Encoding bits

We also need some code to handle returning responses:

```scala
package com.redbubble.util.http

import cats.Show
import com.redbubble.util.cats.ShowOps
import com.redbubble.util.io.BufOps._
import com.redbubble.util.io.Charset.DefaultCharset
import com.redbubble.util.json.JsonPrinter
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.Version.Http11
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.Future
import io.circe.{Encoder, Json}
import io.finch.{Encode, Text}

trait ResponseOps extends JsonPrinter {
  implicit val bufShow: Show[Buf] = ShowOps.showBuf

  final def jsonBuf[A](a: A)(implicit encoder: Encode.Json[A]): Buf = encoder.apply(a, DefaultCharset)

  final def htmlEncode[A](implicit s: Show[A]): Encode.Aux[A, Text.Html] =
    Encode.instance[A, Text.Html]((a, charset) => stringToBuf(s.show(a), charset))

  /**
    * Encode a response, where the payload is placed as the root node in the returned JSON, so without an enclosing
    * `data` field. Use this function sparingly, only for `Json` that already includes the field. In general, we always
    * want to return a top-level data element.
    */
  final def rootJsonEncode[A](implicit encoder: Encoder[A]): Encode.Json[A] =
    Encode.json { (a, _) =>
      byteBufferToBuf(jsonToByteBuffer(encoder.apply(a)))
    }

  /**
    * Encode a response, with an enclosing `data` field at the root of the returned JSON.
    */
  final def dataJsonEncode[A](implicit encoder: Encoder[A]): Encode.Json[A] =
    Encode.json { (a, _) =>
      byteBufferToBuf(jsonToByteBuffer(Json.obj("data" -> encoder.apply(a))))
    }

  final def exceptionJsonEncode(implicit encoder: Encoder[Exception]): Encode.Json[Exception] =
    Encode.json { (e, _) =>
      byteBufferToBuf(jsonToByteBuffer(Json.obj("errors" -> Json.arr(encoder.apply(e)))))
    }

  final def throwableJsonEncode(implicit encoder: Encoder[Throwable]): Encode.Json[Throwable] =
    Encode.json { (e, _) =>
      byteBufferToBuf(jsonToByteBuffer(Json.obj("errors" -> Json.arr(encoder.apply(e)))))
    }

  final def jsonResponse[A](status: Status, a: A, version: Version = Http11)(implicit encode: Encode.Json[A]): Response = {
    val response = newResponse(status, version)
    response.setContentTypeJson()
    response.content = jsonBuf(a)
    response
  }

  final def textResponse(status: Status, content: Buf, version: Version = Http11): Response = {
    val response = newResponse(status, version)
    response.headerMap.add(Fields.ContentLength, content.length.toString)
    response.headerMap.add(Fields.ContentLanguage, "en")
    response.headerMap.add(Fields.ContentType, "text/plain")
    response.content = content
    response
  }

  final def htmlResponse(status: Status, content: AsyncStream[Buf], version: Version = Http11): Response = {
    val response = newResponse(status, version)
    response.headerMap.add(Fields.ContentLanguage, "en")
    response.headerMap.add(Fields.ContentType, "text/html")
    response.setChunked(true)
    val writable = response.writer
    var length = 0
    content.foreachF { chunk =>
      length += chunk.length
      writable.write(chunk)
    }.ensure {
      writable.close()
      ()
    }
    response.headerMap.add(Fields.ContentLength, length.toString)
    response
  }

  final def unauthorised(message: String): Future[Response] =
    Future.value(ResponseOps.textResponse(Status.Unauthorized, Buf.Utf8(message)))

  private val formatter = RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

  private def currentTime(): String = {
    val time = System.currentTimeMillis()
	formatter.format(Instant.ofEpochMilli(time))
  }

  private def newResponse(status: Status, version: Version) = {
    val response = Response()
    response.status = status
    response.version = version
    response.date = currentTime()
    response
  }
}

object ResponseOps extends ResponseOps
```

And utilities for dealing with `Buf`s:

```scala
package com.redbubble.util.io

import java.nio.charset.{Charset => NioCharset}
import java.nio.{ByteBuffer, CharBuffer}

import com.redbubble.util.io.Charset.DefaultCharset
import com.twitter.finagle.netty3.ChannelBufferBuf.Owned
import com.twitter.io.Buf
import com.twitter.io.Charsets._

trait BufOps {
  final def bufToByteArray(buf: Buf): Array[Byte] = {
    val extracted = Owned.extract(buf)
    val rawArray = extracted.array()
    val size = extracted.readableBytes()
    if (rawArray.length == size) {
      rawArray
    } else {
      val dst = new Array[Byte](size)
      System.arraycopy(extracted.array(), 0, dst, 0, size)
      dst
    }
  }

  final def bufToByteBuffer(buf: Buf): ByteBuffer = Owned.extract(buf).toByteBuffer()

  final def byteBufferToBuf(bytes: ByteBuffer): Buf = Buf.ByteBuffer.Owned(bytes)

  final def stringToBuf(s: String, charset: NioCharset = DefaultCharset): Buf = {
    val cb = CharBuffer.wrap(s.toCharArray)
    val encoded = encoder(charset).encode(cb)
    byteBufferToBuf(encoded)
  }

  final def bufToString(buf: Buf, charset: NioCharset = DefaultCharset): String = {
    val output = new Array[Byte](buf.length)
    buf.write(output, 0)
    new String(output, charset)
  }
}

object BufOps extends BufOps
```

## Twitter -> Scala Conversions

Conversion code. You can also use [Bijections](https://github.com/twitter/bijection). Meh.

`Future` conversions.

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

Syntax for `Future` conversions:

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

Conversions for `Try`:

```scala
package com.redbubble.util.std

import com.twitter.util.{Return, Throw, Try => TwitterTry}

import scala.util.{Failure, Success, Try => ScalaTry}

trait OptionOps {
  def asScalaTry[A](o: Option[A], onNone: Throwable): ScalaTry[A] = o.fold[ScalaTry[A]](Failure(onNone))(a => Success(a))

  def asTwitterTry[A](o: Option[A], onNone: Throwable): TwitterTry[A] = o.fold[TwitterTry[A]](Throw(onNone))(a => Return(a))
}

object OptionOps extends OptionOps
```

And some syntax:

```scala
package com.redbubble.util.std

import com.twitter.util.{Try => TwitterTry}

import scala.util.{Try => ScalaTry}

package object syntax {

  implicit final class RichOption[+A](val option: Option[A]) extends AnyVal {
    def toScalaTry(onNone: Throwable): ScalaTry[A] = OptionOps.asScalaTry(option, onNone)

    def toTwitterTry(onNone: Throwable): TwitterTry[A] = OptionOps.asTwitterTry(option, onNone)
  }

}
```
