package com.library.model;

public class Branch {

    private final int id;
    private final String name;
    private final String department;
    private final String code;

    public Branch(int id, String name, String department, String code) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.code = code;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return name + (department != null && !department.isBlank()
            ? " (" + department + ")"
            : "");
    }
}
