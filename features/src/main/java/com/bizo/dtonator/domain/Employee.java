package com.bizo.dtonator.domain;

import java.util.List;
import java.util.Set;

public class Employee {

  private Long id;
  private String name;
  private boolean working;
  private EmployeeType type;
  private Dollars salary;
  private List<EmployeeAccount> accounts;
  private Employer employer;
  private Set<Role> roles;

  public Employee() {
  }

  public Employee(final Long id, final String name) {
    setId(id);
    setName(name);
  }

  public Employee(final String name) {
    setName(name);
  }

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public boolean isWorking() {
    return working;
  }

  public void setWorking(final boolean working) {
    this.working = working;
  }

  public EmployeeType getType() {
    return type;
  }

  public void setType(final EmployeeType type) {
    this.type = type;
  }

  public Dollars getSalary() {
    return salary;
  }

  public void setSalary(final Dollars salary) {
    this.salary = salary;
  }

  public Employer getEmployer() {
    return employer;
  }

  public void setEmployer(final Employer employer) {
    this.employer = employer;
  }

  public List<EmployeeAccount> getAccounts() {
    return accounts;
  }

  public void setAccounts(final List<EmployeeAccount> accounts) {
    this.accounts = accounts;
  }

  public Set<Role> getRoles() {
    return roles;
  }

  public void setRoles(Set<Role> roles) {
    this.roles = roles;
  }

}
