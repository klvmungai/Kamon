/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import java.util.concurrent.ThreadLocalRandom
import kamon.trace.SpanContext.SamplingDecision

trait Sampler {
  def decide(operationName: String, builderTags: Map[String, String]): SamplingDecision
}

object Sampler {
  val always = new Constant(SamplingDecision.Sample)
  val never = new Constant(SamplingDecision.DoNotSample)

  def random(chance: Double): Sampler = {
    assert(chance >= 0D && chance <= 1.0D, "Change should be >= 0 and <= 1.0")

    chance match {
      case 0D       => never
      case 1.0D     => always
      case anyOther => new Random(anyOther)
    }
  }

  class Constant(decision: SamplingDecision) extends Sampler {
    override def decide(operationName: String, builderTags: Map[String, String]): SamplingDecision = decision

    override def toString: String =
      s"Sampler.Constant(decision = $decision)"
  }

  class Random(chance: Double) extends Sampler {
    val upperBoundary = Long.MaxValue * chance
    val lowerBoundary = -upperBoundary

    override def decide(operationName: String, builderTags: Map[String, String]): SamplingDecision = {
      val random = ThreadLocalRandom.current().nextLong()
      if(random >= lowerBoundary && random <= upperBoundary) SamplingDecision.Sample else SamplingDecision.DoNotSample
    }

    override def toString: String =
      s"Sampler.Random(chance = $chance)"
  }
}
