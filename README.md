# searchEngine
This program is a search and indexing engine. It's a final solo project from Skillbox.ru's 'Profession Java-Developer' courses. It is currently a work-in-progress, and it's about 95% done. The project is yet to have a peer review to be considered completed.

## Content
- [Project technologies](#project-technologies)
- [Setup](#setup)
- [How to use](#how-to-use)
- [Contributors](#contributors)

## Project technologies
### Backend:
- Java Core
- [Spring](https://spring.io/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [MySQL](https://www.mysql.com/)
- [Lucene Morphology](https://mvnrepository.com/artifact/org.apache.lucene.morphology)
- [Jsoup](https://jsoup.org/)
- [Lombok](https://projectlombok.org/)
- [Hibernate](https://hibernate.org/)

### Frontend:
- HTML/CSS
- JavaScript
- [jQuery](https://jquery.com/)

## Setup

### You must...
0. Clone the repository to a location of your choice;
1. Install [MySQL Community](https://dev.mysql.com/downloads/installer/) if not yet installed on your PC;
2. Using MySQL Workbench, login to your database and create schema `search_engine`;
3. Open project directory and find project configuration file at `../src/main/resources/application.yaml`.
  Set up database connectivity details in the config file. For example, if your MySQL database runs on port 3306, and login/password are admin/password, you will need to set up the following parameters in `spring.datasource`:
```
spring:
  datasource:
    username: admin
    password: password
    url: jdbc:mysql://localhost:3306/search_engine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true
```

4. You should be good to go! Start the application by running `../src/main/java/searchengine/Application.java`

### You can or should... 
- Set up websites that you wish the program to work with. Set up as many websites as you want in `application.sites`. All website URLs must be provided without a last slash, akin to example below:

```
application:
  sites:
  - url: http://www.some-website.com
    name: Website one
  - url: http://www.some-other-website.com
    name: Website two
  ...
```

- Set `application.debug` to true or false, depending on whether you want to have debug console output:

```
application:
  debug: true
```

- Adjust search settings:
  1) `application.search.snippet-radius` allows to pick bigger or smaller parts of text that contains matches from your search request. Snippet radius ignores the found word, e.g. if you set it to 1, you will receive snippets of text up to 3 words, depending on surrounding HTML tags;
  2) `application.search.max-relative-frequency` is a parameter that allows you to remove relatively popular words from your search request, its value ranging from 0.0 to 1.0:

```
application:
  search:
    snippet-radius: 5
    max-relative-frequency: 0.95
```

- Finally, set up a port of your choice for the program by setting `server.port` to a desired value:

```
server:
  port: 7777
```

## How to use

Go to the Search Engine's web interface at `http://localhost:<server.port>/`.

There are three tabs at web interface:
1. Dashboard - you can find total amount of sites, webpages and lemmas here, as well as websites' info and indexing status.
2. Management - here you can either start indexing all of the websites set up in `application.yaml`, or index a specific page of a website.
  You will not be able to index a page from a website that was not configured in `application.yaml`.
  You can stop indexing websites at any time. Indexing websites may take a while, since the program has to go through each of the pages at the website.
3. Search - in this tab, you can send a query and search for matches on either all websites, or a single one of your choice.

## Contributors

[VirusMS / Sergei M.](https://github.com/VirusMS), yours truly - backend;

[sortedmap / Daniil Pilipenko](https://github.com/sortedmap), [sendelufa / Konstantin Shibkov](https://github.com/sendelufa), [TeslA1402 / Ivan Timeev](https://github.com/TeslA1402) - [frontend base for the project](https://github.com/sortedmap/searchengine)
