/*
 * Copyright (C) 2011-2012 spray.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.httpx

import akka.util.{Timeout, NonFatal}
import akka.util.duration._
import akka.actor.ActorRefFactory
import cc.spray.util.identityFunc
import cc.spray.http.{ContentType, HttpEntity}
import java.util.concurrent.TimeUnit


package object marshalling {

  type ContentTypeSelector = ContentType => Option[ContentType]
  type AcceptableContentTypes = Seq[ContentType]

  def marshal[T](value: T)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                           timeout: Timeout = 1.second): Either[Throwable, HttpEntity] = {
    val ctx = marshalCollecting(value)
    ctx.entity match {
      case Some(entity) => Right(entity)
      case None =>
        Left(ctx.error.getOrElse(new RuntimeException("Marshaller for %s did not produce result" format value)))
    }
  }

  def marshalCollecting[T](value: T)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                                     timeout: Timeout = 1.second): CollectingMarshallingContext = {
    val ctx = new CollectingMarshallingContext
    try {
      marshaller(Some(_)) match { // we always convert to the first CT the marshaller can marshal to
        case Right(marshalling) =>
          marshalling.runSafe(value, ctx)
          ctx.latch.await(timeout.duration.toMillis, TimeUnit.MILLISECONDS)
        case Left(_) =>
          ctx.handleError(new RuntimeException("Marshaller did non produce a marshalling"))
      }
    } catch {
      case NonFatal(e) => ctx.handleError(e)
    }
    ctx
  }

  def marshalUnsafe[T :Marshaller](value: T): HttpEntity = marshal(value).fold(throw _, identityFunc)
}

