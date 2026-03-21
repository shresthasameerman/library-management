# Library Management System (JavaFX + SQLite)

A simple **Library Management** desktop application built with **Java 21**, **JavaFX**, and **SQLite**, using **Maven** for builds.  
The app uses a layered structure (`controller`, `service`, `model`, `database`, `util`) and includes **BCrypt** for password hashing.

---

## Tech Stack

- **Java**: 21
- **UI**: JavaFX (Controls + FXML)
- **Database**: SQLite (via `sqlite-jdbc`)
- **Security**: BCrypt (`jbcrypt`) for password hashing
- **Build Tool**: Maven
- **Packaging**: Maven Shade Plugin (fat JAR)

---

## Project Structure

Main entry point:

- `src/main/java/com/library/App.java`

Key packages:

- `src/main/java/com/library/controller` — UI controllers (JavaFX)
- `src/main/java/com/library/service` — business logic/services
- `src/main/java/com/library/model` — data models/entities
- `src/main/java/com/library/database` — DB connection / queries / DAO layer
- `src/main/java/com/library/util` — helper utilities

---

## Requirements

- Java **21** installed
- Maven installed (or use Maven wrapper if you add one later)

---

## Setup & Run

### 1) Clone the repo
```bash
git clone https://github.com/shresthasameerman/library-management.git
cd library-management
```

### 2) Run with Maven (JavaFX plugin)
Your `pom.xml` is configured to run the app using JavaFX Maven Plugin with:

- Main class: `com.library.App`

Run:
```bash
mvn javafx:run
```

---

## Build

### Build a runnable (fat) JAR
This project uses the **Maven Shade Plugin** to create a fat JAR during `package`.

```bash
mvn clean package
```

After build, check `target/` for the generated JAR.

> Note: the repository currently includes a `target/` directory. Usually, `target/` is not committed to Git and is added to `.gitignore`.

---

## Database (SQLite)

This project includes SQLite support via:
- `org.xerial:sqlite-jdbc`

The DB setup/connection logic is expected inside:
- `src/main/java/com/library/database`

If your app creates the DB file automatically, mention the file path here (edit this section as needed).

---

## Security (Password Hashing)

This project includes BCrypt via:
- `org.mindrot:jbcrypt`

Use it to store hashed passwords rather than plain-text credentials.

---

## Contributing

Contributions are welcome:
1. Fork the repository  
2. Create a feature branch  
3. Commit your changes  
4. Open a pull request

---
