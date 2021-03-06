package com.bizo.dtonator.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.bizo.dtonator.dtos.EmployeeExtensionDto;
import com.bizo.dtonator.dtos.RedAccountDto;
import com.bizo.dtonator.mapper.DefaultDollarsMapper;
import com.bizo.dtonator.mapper.Mapper;

public class EmployeeExtensionDtoTest {

  private final StubEmployeeExtensionMapper employeeExtMapper = new StubEmployeeExtensionMapper();
  private final StubAccountMapper accountMapper = new StubAccountMapper("1");
  private final Mapper mapper = new Mapper(null, null, accountMapper, employeeExtMapper, null, new DefaultDollarsMapper());

  @Test
  public void testToDto() {
    final Employee e = new Employee();
    e.setId(1l);

    final EmployeeExtensionDto dto = mapper.toEmployeeExtensionDto(e);
    assertThat(dto.id, is(1l));
    // hard coded to 1
    assertThat(dto.extensionValue, is(1));
  }

  @Test
  public void testFromDto() {
    final EmployeeExtensionDto dto = new EmployeeExtensionDto(1l, 2);
    final Employee e = new Employee();
    mapper.fromDto(e, dto);
    assertThat(employeeExtMapper.extensionValues, contains(2));
  }

  @Test
  public void testForceMapperMethods() {
    RedAccountDto d = new RedAccountDto(null, "a", false);
    RedAccount a = mapper.fromDto(d);
    assertThat(a.getName(), is("a1"));

    RedAccountDto d2 = mapper.toDto(a);
    assertThat(d2.name, is("a11"));
  }

}
