package com.bizo.dtonator.config;

import static com.google.common.collect.Maps.newHashMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.bizo.dtonator.properties.StubTypeOracle;

public class DtoConfigTest {

  private final StubTypeOracle oracle = new StubTypeOracle();
  private final Map<String, Object> root = newHashMap();
  private final RootConfig rootConfig = new RootConfig(oracle, root);

  @Before
  public void setupRootConfig() {
    final Map<String, Object> config = newHashMap();
    config.put("domainPackage", "com.domain");
    root.put("config", config);
  }

  @Test
  public void testAllProperties() {
    // given two properties
    oracle.addProperty("com.domain.Foo", "a", "java.lang.String");
    oracle.addProperty("com.domain.Foo", "b", "java.lang.String");
    // and no overrides
    final Map<String, Object> map = newHashMap();
    map.put("domain", "Foo");
    // when asked
    final DtoConfig dc = new DtoConfig(oracle, rootConfig, "FooDto", map);
    // then we have both
    assertThat(dc.getProperties().size(), is(2));
  }

  @Test
  public void testPropertiesExclusion() {
    // given two properties
    oracle.addProperty("com.domain.Foo", "a", "java.lang.String");
    oracle.addProperty("com.domain.Foo", "b", "java.lang.String");
    // and an override to skip b
    final Map<String, Object> map = newHashMap();
    map.put("domain", "Foo");
    map.put("properties", "-b");
    // when asked
    final DtoConfig dc = new DtoConfig(oracle, rootConfig, "FooDto", map);
    // then we have only 1
    assertThat(dc.getProperties().size(), is(1));
  }

  @Test
  public void testPropertiesInclusion() {
    // given two properties
    oracle.addProperty("com.domain.Foo", "a", "java.lang.String");
    oracle.addProperty("com.domain.Foo", "b", "java.lang.String");
    // and an override to skip b
    final Map<String, Object> map = newHashMap();
    map.put("domain", "Foo");
    map.put("properties", "a");
    // when asked
    final DtoConfig dc = new DtoConfig(oracle, rootConfig, "FooDto", map);
    // then we have only 1
    assertThat(dc.getProperties().size(), is(1));
  }

  @Test
  public void testPropertiesOnlySupportedOne() {
    // given two properties
    oracle.addProperty("com.domain.Foo", "a", "java.lang.String");
    oracle.addProperty("com.domain.Foo", "b", "java.lang.String");
    // and an override to skip b
    final Map<String, Object> map = newHashMap();
    map.put("domain", "Foo");
    map.put("properties", "a, -b");
    // when asked
    final DtoConfig dc = new DtoConfig(oracle, rootConfig, "FooDto", map);
    // it fails
    try {
      dc.getProperties();
      fail();
    } catch (final IllegalArgumentException iae) {
      assertThat(iae.getMessage(), is("Can't mix inclusions and exclusions: [a, -b]"));
    }
  }

}
