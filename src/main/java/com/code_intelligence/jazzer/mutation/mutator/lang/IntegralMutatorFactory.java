/*
 * Copyright 2023 Code Intelligence GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.code_intelligence.jazzer.mutation.mutator.lang;

import static com.code_intelligence.jazzer.mutation.support.Preconditions.require;
import static java.lang.String.format;

import com.code_intelligence.jazzer.mutation.annotation.InRange;
import com.code_intelligence.jazzer.mutation.api.Debuggable;
import com.code_intelligence.jazzer.mutation.api.MutatorFactory;
import com.code_intelligence.jazzer.mutation.api.PseudoRandom;
import com.code_intelligence.jazzer.mutation.api.SerializingMutator;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.LongStream;

final class IntegralMutatorFactory extends MutatorFactory {
  @Override
  public Optional<SerializingMutator<?>> tryCreate(AnnotatedType type, MutatorFactory factory) {
    if (!(type.getType() instanceof Class)) {
      return Optional.empty();
    }
    Class<?> clazz = (Class<?>) type.getType();

    if (clazz == byte.class || clazz == Byte.class) {
      return Optional.of(new AbstractIntegralMutator<Byte>(type, Byte.MIN_VALUE, Byte.MAX_VALUE) {
        @Override
        public Byte init(PseudoRandom prng) {
          return (byte) initImpl(prng);
        }

        @Override
        public Byte mutate(Byte value, PseudoRandom prng) {
          return (byte) mutateImpl(value, prng);
        }

        @Override
        public Byte read(DataInputStream in) throws IOException {
          return (byte) forceInRange(in.readByte());
        }

        @Override
        public void write(Byte value, DataOutputStream out) throws IOException {
          out.writeByte(value);
        }
      });
    } else if (clazz == short.class || clazz == Short.class) {
      return Optional.of(
          new AbstractIntegralMutator<Short>(type, Short.MIN_VALUE, Short.MAX_VALUE) {
            @Override
            public Short init(PseudoRandom prng) {
              return (short) initImpl(prng);
            }

            @Override
            public Short mutate(Short value, PseudoRandom prng) {
              return (short) mutateImpl(value, prng);
            }

            @Override
            public Short read(DataInputStream in) throws IOException {
              return (short) forceInRange(in.readShort());
            }

            @Override
            public void write(Short value, DataOutputStream out) throws IOException {
              out.writeShort(value);
            }
          });
    } else if (clazz == int.class || clazz == Integer.class) {
      return Optional.of(
          new AbstractIntegralMutator<Integer>(type, Integer.MIN_VALUE, Integer.MAX_VALUE) {
            @Override
            public Integer init(PseudoRandom prng) {
              return (int) initImpl(prng);
            }

            @Override
            public Integer mutate(Integer value, PseudoRandom prng) {
              return (int) mutateImpl(value, prng);
            }

            @Override
            public Integer read(DataInputStream in) throws IOException {
              return (int) forceInRange(in.readInt());
            }

            @Override
            public void write(Integer value, DataOutputStream out) throws IOException {
              out.writeInt(value);
            }
          });
    } else if (clazz == long.class || clazz == Long.class) {
      return Optional.of(new AbstractIntegralMutator<Long>(type, Long.MIN_VALUE, Long.MAX_VALUE) {
        @Override
        public Long init(PseudoRandom prng) {
          return initImpl(prng);
        }

        @Override
        public Long mutate(Long value, PseudoRandom prng) {
          return mutateImpl(value, prng);
        }

        @Override
        public Long read(DataInputStream in) throws IOException {
          return forceInRange(in.readLong());
        }

        @Override
        public void write(Long value, DataOutputStream out) throws IOException {
          out.writeLong(value);
        }
      });
    } else {
      return Optional.empty();
    }
  }

  // Based on
  // https://github.com/google/fuzztest/blob/a663ded6c36f050fbdc634a8fc81d553068d71d7/fuzztest/internal/domain.h#L1447
  // SPDX: Apache-2.0
  // Copyright 2022 Google LLC
  //
  // Visible for testing.
  static abstract class AbstractIntegralMutator<T extends Number> extends SerializingMutator<T> {
    private static final long RANDOM_WALK_RANGE = 5;
    private final long minValue;
    private final long maxValue;
    private final int largestMutableBitNegative;
    private final int largestMutableBitPositive;
    private final long[] specialValues;

    AbstractIntegralMutator(
        AnnotatedType type, long defaultMinValueForType, long defaultMaxValueForType) {
      long minValue = defaultMinValueForType;
      long maxValue = defaultMaxValueForType;
      // InRange is not repeatable, so the loop body will apply exactly once.
      for (Annotation annotation : type.getAnnotations()) {
        if (annotation instanceof InRange) {
          InRange inRange = (InRange) annotation;
          // Since we use a single annotation for all integral types and its min and max fields are
          // longs, we have to ignore them if they are at their default values.
          //
          // This results in a small quirk that is probably acceptable: If someone specifies
          // @InRange(max = Long.MAX_VALUE) on a byte, we will not fail but silently use
          // Byte.MAX_VALUE instead. IDEs will warn about the redundant specification of the default
          // value, so this should not be a problem in practice.
          if (inRange.min() != Long.MIN_VALUE) {
            require(inRange.min() >= defaultMinValueForType,
                format("@InRange.min=%d is out of range: %s", inRange.min(), type.getType()));
            minValue = inRange.min();
          }
          if (inRange.max() != Long.MAX_VALUE) {
            require(inRange.max() <= defaultMaxValueForType,
                format("@InRange.max=%d is out of range: %s", inRange.max(), type.getType()));
            maxValue = inRange.max();
          }
        }
      }

      require(minValue <= maxValue,
          format("[%d, %d] is not a valid interval: %s", minValue, maxValue, type));
      require(minValue != maxValue,
          format(
              "[%d, %d] can not be mutated, use a constant instead: %s", minValue, maxValue, type));
      this.minValue = minValue;
      this.maxValue = maxValue;
      if (minValue >= 0) {
        largestMutableBitNegative = 0;
        largestMutableBitPositive = bitWidth(minValue ^ maxValue);
      } else if (maxValue < 0) {
        largestMutableBitNegative = bitWidth(minValue ^ maxValue);
        largestMutableBitPositive = 0;
      } else /* minValue < 0 && maxValue >= 0 */ {
        largestMutableBitNegative = bitWidth(~minValue);
        largestMutableBitPositive = bitWidth(maxValue);
      }
      this.specialValues = collectSpecialValues(minValue, maxValue);
    }

    private static long[] collectSpecialValues(long minValue, long maxValue) {
      // Special values can collide or not apply when @InRange is used, so filter appropriately and
      // remove duplicates - we don't want to weigh certain special values higher than others.
      return LongStream.of(0, 1, minValue, maxValue)
          .filter(value -> value >= minValue)
          .filter(value -> value <= maxValue)
          .distinct()
          .sorted()
          .toArray();
    }

    private static int bitWidth(long value) {
      return 64 - Long.numberOfLeadingZeros(value);
    }

    protected final long initImpl(PseudoRandom prng) {
      int sentinel = specialValues.length;
      int choice = prng.closedRange(0, sentinel);
      if (choice < sentinel) {
        return specialValues[choice];
      } else {
        return prng.closedRange(minValue, maxValue);
      }
    }

    protected final long mutateImpl(long value, PseudoRandom prng) {
      final long previousValue = value;
      // Mutate in a loop to verify that we really mutated.
      do {
        // TODO: Mutate using value profile/trace cmp feedback.
        if (prng.trueInOneOutOf(4)) {
          value = bitFlip(value, prng);
        } else if (prng.choice()) {
          value = randomWalk(value, prng);
        } else {
          value = prng.closedRange(minValue, maxValue);
        }
      } while (value == previousValue);
      return value;
    }

    /**
     * Force value into the closed interval [minValue, maxValue] while preserving as many of its
     * bits as possible (e.g. so that mutations that apply to the raw byte representation still have
     * a good chance to actually mutate the value). Clamping would not have this property.
     */
    protected final long forceInRange(long value) {
      // Fast path for the common case.
      if (value >= minValue && value <= maxValue) {
        return value;
      }
      return forceInRange(value, minValue, maxValue);
    }

    // Visible for testing.
    static long forceInRange(long value, long minValue, long maxValue) {
      long range = maxValue - minValue;
      if (range > 0) {
        return minValue + Math.abs((value - minValue) % range);
      } else {
        // [minValue, maxValue] covers at least half of the [Long.MIN_VALUE, Long.MAX_VALUE] range,
        // so if value doesn't lie in [minValue, maxValue], it will after shifting once.
        if (value >= minValue && value <= maxValue) {
          return value;
        } else {
          return value + range;
        }
      }
    }

    private long bitFlip(long value, PseudoRandom prng) {
      int range = value >= 0 ? largestMutableBitPositive : largestMutableBitNegative;
      value = value ^ (1L << prng.indexIn(range));
      // The bit flip may violate the range constraint, if so, mutate randomly.
      if (value > maxValue || value < minValue) {
        value = prng.closedRange(minValue, maxValue);
      }
      return value;
    }

    private long randomWalk(long value, PseudoRandom prng) {
      // Prevent overflows by averaging the individual bounds.
      if (maxValue / 2 - minValue / 2 <= RANDOM_WALK_RANGE) {
        value = prng.closedRange(minValue, maxValue);
      } else {
        // At this point we know that (using non-wrapping arithmetic):
        // RANDOM_WALK_RANGE < maxValue/2 - minValue/2 <= Long.MAX_VALUE/2 - minValue/2, hence
        // minValue/2 + RANDOM_WALK_RANGE < Long.MAX_VALUE/2, hence
        // minValue + 2*RANDOM_WALK_RANGE < Long.MAX_VALUE.
        // In particular, minValue + RANDOM_WALK_RANGE can't overflow, likewise for maxValue.
        long lower = minValue;
        if (value > lower + RANDOM_WALK_RANGE) {
          lower = value - RANDOM_WALK_RANGE;
        }
        long upper = maxValue;
        if (value < upper - RANDOM_WALK_RANGE) {
          upper = value + RANDOM_WALK_RANGE;
        }
        value = prng.closedRange(lower, upper);
      }
      return value;
    }

    @Override
    public T detach(T value) {
      // Always immutable.
      return value;
    }

    @Override
    public String toDebugString(Predicate<Debuggable> isInCycle) {
      return ((Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass())
                  .getActualTypeArguments()[0])
          .getSimpleName();
    }
  }
}
