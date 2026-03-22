package com.library.model;

import javafx.beans.property.*;

public class Member {

    private final IntegerProperty id;
    private final StringProperty  name;
    private final StringProperty  email;
    private final StringProperty  phone;
    private final StringProperty  memberId;
    private final StringProperty  department;
    private final StringProperty  memberType;   // "Student" or "Staff"
    private final BooleanProperty active;

    public Member(int id, String name, String email, String phone,
                  String memberId, String department,
                  String memberType, boolean active) {
        this.id         = new SimpleIntegerProperty(id);
        this.name       = new SimpleStringProperty(name);
        this.email      = new SimpleStringProperty(email);
        this.phone      = new SimpleStringProperty(phone);
        this.memberId   = new SimpleStringProperty(memberId);
        this.department = new SimpleStringProperty(department);
        this.memberType = new SimpleStringProperty(memberType);
        this.active     = new SimpleBooleanProperty(active);
    }

    // ── ID ────────────────────────────────────────────────────────────
    public int getId()                   { return id.get(); }
    public IntegerProperty idProperty()  { return id; }

    // ── Name ──────────────────────────────────────────────────────────
    public String getName()               { return name.get(); }
    public void   setName(String v)       { name.set(v); }
    public StringProperty nameProperty()  { return name; }

    // ── Email ─────────────────────────────────────────────────────────
    public String getEmail()              { return email.get(); }
    public void   setEmail(String v)      { email.set(v); }
    public StringProperty emailProperty() { return email; }

    // ── Phone ─────────────────────────────────────────────────────────
    public String getPhone()              { return phone.get(); }
    public void   setPhone(String v)      { phone.set(v); }
    public StringProperty phoneProperty() { return phone; }

    // ── Member ID ─────────────────────────────────────────────────────
    public String getMemberId()              { return memberId.get(); }
    public void   setMemberId(String v)      { memberId.set(v); }
    public StringProperty memberIdProperty() { return memberId; }

    // ── Department ────────────────────────────────────────────────────
    public String getDepartment()              { return department.get(); }
    public void   setDepartment(String v)      { department.set(v); }
    public StringProperty departmentProperty() { return department; }

    // ── Member Type ───────────────────────────────────────────────────
    public String getMemberType()              { return memberType.get(); }
    public void   setMemberType(String v)      { memberType.set(v); }
    public StringProperty memberTypeProperty() { return memberType; }

    // ── Active ────────────────────────────────────────────────────────
    public boolean isActive()               { return active.get(); }
    public void    setActive(boolean v)     { active.set(v); }
    public BooleanProperty activeProperty() { return active; }

    // ── Display helpers ───────────────────────────────────────────────
    public String getStatus() {
        return active.get() ? "Active" : "Inactive";
    }

    public String getTypeIcon() {
        return "Staff".equals(memberType.get()) ? "👨‍💼 Staff" : "🎓 Student";
    }

    @Override
    public String toString() {
        return name.get() + " (" + memberId.get() + ")";
    }
}