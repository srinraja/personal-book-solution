# Runbook

This guide provides instructions on how to set up, build, and run the Personal Book application, along with details about the available API endpoints.

## Prerequisites

- **Java 17** or higher
- **Maven 3.6+** (or use the included Maven Wrapper `./mvnw`)

## Build and Run

### Build the Project
To compile the code and package the application, run:
```bash
./mvnw clean install
```

### Run the Application
To start the Spring Boot application locally:
```bash
./mvnw spring-boot:run
```
The application will start on the default port **8080**.

## Running Tests

### Run Unit Tests
To execute the unit tests, run:
```bash
./mvnw test
```

### Run Unit and Integration Tests
To execute both unit and integration tests, run:
```bash
./mvnw verify
```

## API Endpoints

The following endpoints are available for interacting with the application:

### Book Management (Database)

- **`GET http://localhost:8080/books`**
  - **Description**: Retrieves a list of all books currently saved in the local database.
- **`POST http://localhost:8080/books/{googleId}`**
  - **Description**: Fetches book details from Google Books using the provided `googleId` and saves the book to the local database.

### Google Books Integration

- **`GET http://localhost:8080/google?q={query}&maxResults={maxResults}&startIndex={startIndex}`**
  - **Description**: Searches for books on Google Books based on the search query `q`.
  - **Optional Parameters**: `maxResults` (number of items to return), `startIndex` (position to start from).
- **`GET http://localhost:8080/google/{id}`**
  - **Description**: Retrieves detailed information for a specific book from Google Books using its unique `id`.

