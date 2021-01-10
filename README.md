# Datafi    
 Datafi auto-generates a powerful, user friendly data access manager for Spring-Data-Jpa applications.    
    
 - No more boilerplate JPaRepository interfaces.     
 - All of the features of Spring Data JPA without writing a single line of data layer code.    

 * [Installation](#installation)
 * [Hello World](#hello-world)
     - [Domain model](#domain-model)
     - [Service layer](#service-layer)
 * [Archivability](#archivability)
     - [Overview](#overview)
     - [Domain model](#domain-model-1)
     - [Example Service Layer](#example-service-layer)
 * [Custom Queries](#custom-queries)
     - [Overview](#overview-1)
     - [Custom SQL / JPQL query syntax](#custom-sql---jpql-query-syntax)
     - [Arguments](#arguments)
       * [Case 1 - Argument corresponds to field within annotated entity](#case-1---argument-corresponds-to-field-within-annotated-entity)
       * [Case 2 - Argument does not correspond to such a field](#case-2---argument-does-not-correspond-to-such-a-field)
       * [Case 3 - Argument has been previously specified within the same query](#case-3---argument-has-been-previously-specified-within-the-same-query)
     - [Annotations](#annotations)
	 - [Return Types](#return-types)
     - [Usage](#usage)
 * [@FindBy, and @FindAllBy](#-findby--and--findallby)
     - [Domain model](#domain-model-2)
     - [Example Service Layer](#example-service-layer-1)
     - [Domain model](#domain-model-3)
       * [Example Service Layer](#example-service-layer-2)
 * [@FindByUnique](#-findbyunique)
     - [Domain model](#domain-model-4)
     - [Example Service Layer](#example-service-layer-3)
 * [Free text search](#free-text-search)
       * [Domain model](#domain-model-5)
       * [Example Service Layer](#example-service-layer-4)
  + [cascadedUpdate](#cascadedupdate)
  + [cascadeUpdateCollection](#cascadeupdatecollection)
  + [Excluding fields from cascadeUpdate(...)](#excluding-fields-from-cascadeupdate--)
  + [Mutating the state of foreign key Iterables](#mutating-the-state-of-foreign-key-iterables)
 * [Extras](#extras)
   + [StandardPersistableEntity](#standardpersistableentity)
     - [Domain model](#domain-model-6)
   + [IdFactory](#idfactory)
 * [License](#license)

## Installation 
Datafi is available on maven central:  
```  
<dependency>  
	 <groupId>dev.sanda</groupId> 
	 <artifactId>datafi</artifactId> 
	 <version>0.1.0.BETA</version>
</dependency>  
```  
  
## Hello World 
Datafi autogenerates Jpa repositories for all data model entities annotated with `@Entity` and / or `@Table` annotation(s).    
To make use of this, `@Autowire` the `DataManager<T>` bean into your code, as follows:    
    
#### Domain model 
```    
@Entity  
public class Person{    
     @Id   
     @GeneratedValue  
	 private Long id;     
	 private String name;   
	 private Integer age; 
} 
``` 
#### Service layer 
Now any `JpaRepository` or `JpaSpecificationExecutor` method can be called. For example: `findById(id)` 
``` 
@Service public class PersonService{   
	 @Autowired     
	 private DataManager<Person> personDataManager;         
     public Person getPersonById(String id){    
     return personDataManager.findById(id).orElse(...);   
     }  
} 
```    
## Archivability  
  
#### Overview  
Records can be marked as archived, as opposed to actually deleting them from the database. Datafis' `DataManager<T>` bean supports this out of the box with the following four methods:  
1. `public T archive(T input)`: Finds the `input` record by id, and marks it as archived.  
2. `public T deArchive(T input)`: The opposite of 1.  
3. `public List<T> archiveCollection(Collection<T> input)`: 1 in plural.  
4. `public List<T> deArchiveCollection(Collection<T> input)`: 2 in plural.  
  
In order to make use of this feature for a given entity, it must implement the `Archivable` interface, which requires a getter and a setter for a `Boolean isArchived` field.
  
Observe the following example:  
  
#### Domain model
 ``` 
 @Entity  
public class Person implements Archivable{    
	  @Id   
      @GeneratedValue  
	  private Long id;  
	  private String name;   
	  // if using lombok, use Boolean (not boolean) type
	  @Getter @Setter
	  private Boolean isArchived = false;  
	 //...
}
  ```   
#### Example Service Layer
 ```
 @Service 
 public class PersonService{   
	 @Autowired     
	 private DataManager<Person> personDataManager;   
	 
     public Person archivePerson(Person toArchive){  
		 return personDataManager.archive(toArchive); 
	 } 
		     
	 public Person deArchivePerson(Person toDeArchive){  
		 return personDataManager.deArchive(toDeArchive); 
	 }     
		  
	 public List<Person> archivePersons(List<Person> toArchive){  
		 return personDataManager.archiveCollection(toArchive); 
	 }   
		   
	 public List<Person> deArchivePersons(List<Person> toDeArchive){  
		 return personDataManager.deArchiveCollection(toDeArchive); 
	 }
 } 
 ```    

## Custom Queries
#### Overview
The only thing capable of encapsulating the full power of SQL is SQL. That's why `JpaRepository` includes the option of specifying custom SQL queries by annotating a method with a `@Query(value = "...", nativeQuery = true / false)`  annotation (see [here](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods.at-query) for official documentation). Datafi accommodates for this as well, as follows.

#### Custom SQL / JPQL query syntax
The query syntax itself is identical to what it normally be. The two things which do require addressing are arguments and return types.
#### Arguments
Query arguments are inferred from within the query itself. Arguments are specified as follows:
##### Case 1 - Argument corresponds to field within annotated entity
`:<field name>`. For example, given our above `Person` class; `:id`,  `:name`, and is `isArchived` would all be valid argument specifications.
##### Case 2 - Argument does not correspond to such a field
`<argument type>::<argument name>`.  Argument type can be any JAVA primitive type. To specify a list, use `<argument type>[]::<argument name>`. For example; to specify a single string type argument; `String::nickName`. To specify a list of string type arguments; `String[]::nickNames`.
##### Case 3 - Argument has been previously specified within the same query
If the argument name is identical to one which has been previously specified  within the same query, use the syntax from case 1 (i.e. `:<argument name>`).

#### Annotations
This feature can be utilized via the following class level annotations:
1. `@WithQuery(name = "METHOD_NAME_HERE", jpql = "JPQL_QUERY_HERE")`
Example Model:
	```
	@Entity
	@WithQuery(name = "findByNameAndAge", jpql = "SELECT p FROM Person p WHERE p.name = :name AND p.age = :age")
	public class Person {
	    @Id
	    @GeneratedValue
	    private Long id;
	    private String name;
	    private Integer age;
	}
	```
	Resulting generated code:
	```
		@Repository  
		public interface PersonDao extends GenericDao<Long, Person> {
			@Query("SELECT p FROM Person p WHERE p.name = :name AND p.age = :age")
			 List<Person> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
		}
	```
	Breakdown:
	
	When auto-generating the `JpaRepository` for a given entity, Datafi generates any custom methods that have been specified - which in this case would be the above `@WithQuery(...)` annotation. The provided `name` argument is used as the method name, and the `jpql`  argument is the query itself.
2. `@WithNativeQuery(name = "METHOD_NAME_HERE", sql = "NATIVE_SQL_QUERY_HERE")`
Example Model:
	```
	@Entity
	@WithNativeQuery(name = "findByNameAndAge", sql = "SELECT * FROM Person WHERE name = :name AND age = :age")
	public class Person {
	    @Id 
	    @GeneratedValue
	    private Long id;
	    private String name;
	    private Integer age;
	}
	```
	Resulting generated code:
	```
	@Repository  
	public interface PersonDao extends GenericDao<Long, Person> {
		@Query("SELECT * FROM Person WHERE name = :name AND age = :age", nativeQuery = true)
		 List<Person> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
	}
	```
	Breakdown is the same as the previous, except for the `nativeQuery` flag being set to `true`.
	
3. `@WithQueryScripts({"PATH/TO/SOME/FILE_NAME.jpql", "PATH/TO/SOME/OTHER/FILE_NAME.jpql", etc.})`
The above two approaches don't scale very well, and are not ideal for longer queries. This and the next annotation(s) address this by allowing the developer to specify a list of files to be loaded as classpath resources (typically from the root `Resources` directory). The name of each file is used in place of the above `name = "..."` parameter, and the file contents are of course used as the query body. This annotation is for non-native `jpql` queries, and as such all files specified must have a `.jpql` type suffix.  
4. `@WithNativeQueryScripts({"PATH/TO/SOME/FILE_NAME.sql", "PATH/TO/SOME/OTHER/FILE_NAME.sql", etc.})`
Same as the previous, except for the queries being native. This means all files specified must have a `.sql` type suffix.

**Note regarding comments:** _All_ comments within queries must open with `/*` and close with `*/`.
#### Return types
The default query return type is the annotated entity type itself. If the query is JPQL, it can leverage the [dto projection](https://www.baeldung.com/spring-data-jpa-projections) feature in order to return a different type. Whether a single record or a list of records is returned is implied within the query itself. Queries beginning with `INSERT` or `REPLACE` are assumed to return a single record. Likewise, queries ending with `LIMIT 1` are also assumed to return a single record. In all other cases, a list of records will be returned. 
#### Usage
To use such a custom query, call the `public <TResult> TResult callQuery(String queryName, Object... args)` method in `DataManager<T>`. The `queryName` is what it looks like, and the `args` takes in the arguments in the order specified in the corresponding annotation (i.e. The order in which they are specified within the corresponding query itself). 

## @FindBy, and @FindAllBy 
 Class field can be annotated with the `@FindBy` and / or `@FindAllBy` annotation(s), and this will generate a corresponding `findBy...(value)`, or `findAllBy...In(List<...> values)`. For example:    
    
#### Domain model 
``` 
@Entity  
public class Person{    
     @Id   
     private String id = UUID.randomUUID().toString();   
     @FindBy   
     private String name;  
	 @FindAllBy     
	 private Integer age;   
     @FindBy @FindAllBy   
     private String address;   
     // getters & setters, etc..  
} 
``` 
As can be observed, a field can have both annotations at the same time.    
#### Example Service Layer
 ```
 @Service 
 public class PersonService{   
	@Autowired     
	private DataManager<Person> personDataManager;  
	 
	/* corresponds to @FindBy private String name;   
	returns a list of persons with the (same) given name */  
	public List<Person> getPersonsByName(String name){     
	return personDataManager.FindBy("name", name).orElse(...);}       
	  
	//corresponds to @FindAllBy private Integer age;   
	public List<Person> getAllPersonsByAge(List<Integer> ages){   
	 return personDataManager.FindAllBy("age", ages).orElse(...);}       

	//the following two methods correspond to @FindBy @FindAllBy private String address;    
	public List<Person> getPersonsByAddress(String address){   
	 return personDataManager.FindBy("address", address).orElse(...);}   
	  
	public List<Person> getAllPersonsByAddressIn(List<String> addresses){    
		return personDataManager.FindAllBy("address", addresses).orElse(...);}
}
```
#### Domain model 
```  
@Entity  
public class Person{    
     @Id   
     @GeneratedValue
     private Long id;   
     @FindBy   
     private String name;  
	 @FindAllBy     
	 private Integer age;   
     @FindBy @FindAllBy   
     private String address;   
     // getters & setters, etc..  
} 
``` 
As can be observed, a field can have both annotations at the same time.    
##### Example Service Layer
 ``` 
 @Service public class PersonService{   
 @Autowired     private DataManager<Person> personDataManager;   
       
	 /* corresponds to @FindBy private String name;   
	    returns a list of persons with the (same) given name */  
		public List<Person> getPersonsByName(String name){     
			 return personDataManager.FindBy("name", name).orElse(...);}       
       
	   //corresponds to @FindAllBy private Integer age;   
	 public List<Person> getAllPersonsByAge(List<Integer> ages){   
	      return personDataManager.FindAllBy("age", ages).orElse(...);}       
       
     //the following two methods correspond to @FindBy @FindAllBy private String address;    
     public List<Person> getPersonsByAddress(String address){   
        return personDataManager.FindBy("address", address).orElse(...);}    
       
     public List<Person> getAllPersonsByAddressIn(List<String> addresses){    
        return personDataManager.FindAllBy("address", addresses).orElse(...);}
 }
 ```  
## @FindByUnique  
As can be observed, the return type of both of the previous methods is a list. That's because there is no guarantee of uniqueness with regards to a field simply because it's been annotated with `@FindBy` and / or `@FindAllBy`. This is where `@FindByUnique` differs; it takes a unique value argument, and returns a single corresponding entity. In order for this to be valid syntax, any field annotated with the `@FindByUnique` annotation must also be annotated with `@Column(unique = true)`. If a field is annotated with only `@FindByUnique` but not `@Column(unique = true)`, a compilation error will occur. The following is an illustrative example:  
#### Domain model 
``` 
@Entity  
public class Person{   
	 @Id @GeneratedValue
	 private Long id;
	 @FindByUnique @Column(unique = true)
	 private String name;  
	 //...
} 
``` 
#### Example Service Layer 
``` 
@Service 
public class PersonService{   
	 @Autowired
	 private DataManager<Person> personDataManager;   
	 /*   
	 corresponds to   
	 @FindByUnique   
	 private String name;   
	 Returns a single person with the given name  
	*/     
	 public Person getPersonByUniqueName(String name){   
        return personDataManager.FindByUnique( "name", name).orElse(...);
     } 
}   
```  
  
## Free text search  
Datafi comes with non case sensitive free text ("Fuzzy") search out of the box. To make use of this, either one or more **String typed** fields can be annotated with `@FreeTextSearchBy`, or the class itself can be annotated with `@FreeTextSearchByFields({"field1", "field2", etc...})`.  Then the `freeTextSearchBy(String searchTerm, args...)` method in the respective class' `DataManager` can be called.   
  
Observe the following example:  
  
#### Domain model 
``` 
@Entity  
//@FreeTextSearchByFields({"name", "email"}) - this is equivalent to the field level annotations below  
public class Person{   
	 @Id @GeneratedValue
	 private Long id;   
	       
	@FreeTextSearchBy  
	private String name; 
	@FreeTextSearchBy private String email; 
	//...
} 
``` 
#### Example Service Layer 
``` 
@Service public class PersonService{
   
	 @Autowired
	 private DataManager<Person> personDataManager;   
	 
	 public List<Person> freeTextSearchPeople(String searchTerm){   
	        return personDataManager.freeTextSearch(searchTerm);   
	 } 
}   
```  
`freeTextSearch` returns the listed contents of a `Page` object. This means that the search results are paginated by definition. Because of this, `freeTextSearch` takes in the 2 optional arguments `int offset` and `int limit` - in that order. These are "optional" in the sense that if not specified, the offset and limit will default to 0 and 50 respectively. An additional 2 optional arguments are `String sortBy` and `Sort.Direction sortDirection` - in that order. `String sortBy` specifies the name of a field within the given entity by which to apply the sort. If no matching field is found an `IllegalArgumentException` is thrown. `Sort.Direction sortDirection` determines the ordering strategy. If not specified it defaults to ascending order (`ASC`).  
       
## `cascadeUpdate `
One issue which requires attention when designing a data model is cascading. Datafi simplifes this by offering out-of-the-box, built in application layer cascading when applying update operations. See illustration:  
```
@Service public class PersonService{   
	 @Autowired
	 private DataManager<Person> personDataManager;      
	       
	 public Person updatePerson(Person toUpdate, Person objectWithUpdatedValues){    
	    return personDataManager.cascadeUpdate(toUpdate,  objectWithUpdatedValues);  
	}  
} 
```    
 Breakdown:     
 - The first argument is the `Person` instance we wish to update.    
     
 - The second argument is an instance of `Person` containing the updated values to be assigned to the corresponding fields within the first `Person` instance.  All of the it's other fields **must** be null.     
     
    **Important note**: This method skips over any iterables.    
    
## `cascadeUpdateCollection` 
`cascadeUpdateCollection` offers analogous functionality to `cascadeUpdate`, only in plural. For Example:  
    
``` 
@Service public class PersonService{   
 @Autowired
 private DataManager<Person> personDataManager;   
       
     //obviously, these two lists must correspond in length   
     public List<Person> updatePersons(List<Person> toUpdate, List<Person> objectsWithUpdatedValues){   
        return personDataManager.cascadeUpdateCollection(toUpdate, objectsWithUpdatedValues);   
     }  
}
 ```    
 ### Excluding fields from cascadeUpdate(...) 
 operations Field(s) to be excluded from `cascadeUpdate` operations should be annotated as `@NonApiUpdatable`. Alternately, if there are many such fields in a class and the developer would rather avoid the field-level annotational clutter, the class itself can be annotated with `@NonApiUpdatables`, with the relevant field names passed as arguments. For example, the following:  
    
 ``` 
 @Entity    
 public class Person {   
   
     @Id   
     private String id = UUID.randomUUID().toString();  
      @NonApiUpdatable   
     private String name;   
       
     @NonApiUpdatable   
     private Integer age;   
       
     private String address;   
       
 }  
 ```  
 is equivalent to:    
    
 ```
  @Entity 
  @NonApiUpdatables({"name", "age"}) public class Person {   
	 @Id 
	 @GeneratedValue   
	 private Long id;   
	 private String name;   
	 private String address;   
 } 
 ``` 
 ### Mutating the state of foreign key Iterables 
 As metioned above, `cascadeUpdate` operations skip over iterable type fields. This is due to the fact that collection mutations involve adding or removing elements to or from the collection - not mutations on the collection container itself. Therefore, `DataManager<T>` includes the following methods to help with adding to, and removing from, foreign key collections.    
1. `public<HasTs> List<T> createAndAddNewToCollectionIn(HasTs toAddTo, String fieldName, List<T> toAdd)`    
 This method takes in three arguments, while making internal use of the application level `cascadedUpdate` above in order to propogate the relevant state changes:    
   - `HasTs toAddTo` - The entity containing the foriegn key collection of "Ts" (The type of entities referenced in the collection) to which to add.  
   - `String fieldName` - The field name of the foreign key collection (i.e. for `private Set<Person> friends;`, it'd be `"friends"`).   
   - `List<T> toAdd` - The entities to add to the collection.    
2. `public<HasTs> List<T> associateExistingWithCollectionIn(HasTs toAddTo, String fieldName, List<T> toAttach)` Similar to the previous method but for one crucial difference; it ensures the entities to be _attached_ (**not added** from scratch) are indeed **already present** within their respective table within the database.  
    
## Extras
### StandardPersistableEntity  
If you don't wan't to worry about assigning `@Id` or `@Version` column. the `StandardPersistableEntity` `@MappedSuperclass` can be extended. For example:    
#### Domain model
 ``` 
 @Entity 
 public class Person extends StandardPersistableEntity {    
    private String name;   
    private Integer age;   
    // getters & setters, etc...  
} 
```    
 ### IdFactory  
Alternately, the unix timestamp based `Long IdFactory.getNextId()` static method can be employed. For example:    
    
``` @Entity public class Person{    
     @Id   
     private Long id = IdFactory.getNextId();   
     private String name;   
     private Integer age;   
     // getters & setters, etc...  
} 
```   
    
That's all for now, happy coding! 
## License  
 [Apache 2.0](https://github.com/sanda-dev/datafi/blob/master/LICENSE)# Datafi    
 Datafi auto-generates a powerful, user friendly data access manager for Spring-Data-Jpa applications.    
    
 - No more boilerplate JPaRepository interfaces.    
 - Custom Jpa resolvers with a few simple field-level annotations.    
 - Get all the features of Jpa for your entire data model, without writing a single line of data layer code.    

## Installation 
Datafi is available on maven central:  
```  
<dependency>  
	 <groupId>dev.sanda</groupId> 
	 <artifactId>datafi</artifactId> 
	 <version>0.0.3</version>
</dependency>  
```  
  
## Hello World 
Datafi autogenerates Jpa repositories for all data model entities annotated with `@Entity` and / or `@Table` annotation(s).    
To make use of this, `@Autowire` the `DataManager<T>` bean into your code, as follows:    
    
#### Domain model 
```    
@Entity  
public class Person{    
     @Id   
     @GeneratedValue  
	 private Long id;     
	 private String name;   
	 private Integer age; 
} 
``` 
#### Service layer 
Now any `JpaRepository` or `JpaSpecificationExecutor` method can be called. For example: `findById(id)` 
``` 
@Service public class PersonService{   
	 @Autowired     
	 private DataManager<Person> personDataManager;         
     public Person getPersonById(String id){    
     return personDataManager.findById(id).orElse(...);   
     }  
} 
```    
## Archivability  
  
#### Overview  
Sometimes when it comes to removing records from a database, the choice is made to mark the relevant records as archived, as oppposed to actually deleting them from the database. Datafi `DataManager<T>` supports this out of the box with the following four methods:  
1. `public T archive(T input)`: Finds the `input` record by id, and marks it as archived.  
2. `public T deArchive(T input)`: The opposite of 1.  
3. `public List<T> archiveCollection(Collection<T> input)`: 1 in plural.  
4. `public List<T> deArchiveCollection(Collection<T> input)`: 2 in plural.  
  
In order to make use of this feature for a given entity, it must implement the `Archivable` interface, the implementation of which requires a getter and a setter for a `Boolean isArchived` field.
  
Observe the following example:  
  
#### Domain model
 ``` 
 @Entity  
public class Person implements Archivable{    
	  @Id   
      @GeneratedValue  
	  private Long id;  
	  private String name;   
	  // if using lombok, use Boolean (not boolean) type
	  @Getter @Setter
	  private Boolean isArchived = false;  
	 //...
}
  ```   
#### Example Service Layer
 ```
 @Service 
 public class PersonService{   
	 @Autowired     
	 private DataManager<Person> personDataManager;   
	 
     public Person archivePerson(Person toArchive){  
		 return personDataManager.archive(toArchive); 
	 } 
		     
	 public Person deArchivePerson(Person toDeArchive){  
		 return personDataManager.deArchive(toDeArchive); 
	 }     
		  
	 public List<Person> archivePersons(List<Person> toArchive){  
		 return personDataManager.archiveCollection(toArchive); 
	 }   
		   
	 public List<Person> deArchivePersons(List<Person> toDeArchive){  
		 return personDataManager.deArchiveCollection(toDeArchive); 
	 }
 } 
 ```    

## Custom Queries
#### Overview
The only thing capable of encapsulating the full power of SQL is SQL. That's why `JpaRepository` includes the option of specifying custom SQL queries by annotating a method with a `@Query(value = "...", nativeQuery = true / false)`  annotation (see [here](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods.at-query) for official documentation). 

#### Custom SQL / JPQL query syntax
The query syntax itself is identical to what it normally be. The two things which do require addressing are arguments and return types.
#### Arguments
Query arguments are inferred from within the query itself. Arguments are specified as follows:
##### Case 1 - Argument corresponds to field within annotated entity
`:<field name>`. For example, given our above `Person` class; `:id`,  `:name`, and is `isArchived` would all be valid argument specifications.
##### Case 2 - Argument does not correspond to such a field
`<argument type>::<argument name>`.  Argument type can be any JAVA primitive type. To specify a list, use `<argument type>[]::<argument name>`. For example; to specify a single string type argument; `String::name`. To specify a list of string type arguments; `String[]::names`.
##### Case 3 - Argument has been previously specified within the same query
If the argument name is identical to one which has been previously specified  within the same query, use the syntax from case 1 (i.e. `:<argument name>`).
This feature can be utilized via the following class level annotations:
#### Annotations
1. `@WithQuery(name = "METHOD_NAME_HERE", jpql = "JPQL_QUERY_HERE")`
Example Model:
	```
	@Entity
	@WithQuery(name = "findByNameAndAge", jpql = "SELECT p FROM Person p WHERE p.name = :name AND p.age = :age")
	public class Person {
	    @Id
	    @GeneratedValue
	    private Long id;
	    private String name;
	    private Integer age;
	}
	```
	Resulting generated code:
	```
		@Repository  
		public interface PersonDao extends GenericDao<Long, Person> {
			@Query("SELECT p FROM Person p WHERE p.name = :name AND p.age = :age")
			 List<Person> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
		}
	```
	Breakdown:
	
	When auto-generating the `JpaRepository` for a given entity, Datafi generates any custom methods that have been specified - which in this case would be the above `@WithQuery(...)` annotation. The provided `name` argument is used as the method name, and the `jpql`  argument is the query itself.
2. `@WithNativeQuery(name = "METHOD_NAME_HERE", sql = "NATIVE_SQL_QUERY_HERE")`
Example Model:
	```
	@Entity
	@WithNativeQuery(name = "findByNameAndAge", sql = "SELECT * FROM Person WHERE name = :name AND age = :age")
	public class Person {
	    @Id 
	    @GeneratedValue
	    private Long id;
	    private String name;
	    private Integer age;
	}
	```
	Resulting generated code:
	```
	@Repository  
	public interface PersonDao extends GenericDao<Long, Person> {
		@Query("SELECT * FROM Person WHERE name = :name AND age = :age", nativeQuery = true)
		 List<Person> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);
	}
	```
	Breakdown is the same as the previous, except for the `nativeQuery` flag being set to `true`.
	
3. `@WithQueryScripts({"PATH/TO/SOME/FILE_NAME.jpql", "PATH/TO/SOME/OTHER/FILE_NAME.jpql", etc.})`
The above two approaches don't scale very well, and are not ideal for longer queries. This and the next annotation(s) address this by allowing the developer to specify a list of files to be loaded as classpath resources (typically from the root `Resources` directory). The name of each file is used in place of the above `name = "..."` parameter, and the file contents are of course used as the query body. This annotation is for non-native `jpql` queries, and as such all files specified must have a `.jpql` type suffix.  
4. `@WithNativeQueryScripts({"PATH/TO/SOME/FILE_NAME.sql", "PATH/TO/SOME/OTHER/FILE_NAME.sql", etc.})`
Same as the previous, except for the queries being native. This means all files specified must have a `.sql` type suffix.



## @FindBy, and @FindAllBy 
 Any field can be annotated with the `@FindBy` and / or `@FindAllBy` annotation(s), and this will generate a corresponding `findBy...(value)`, or `findAllBy...In(List<...> values)`. For example:    
    
#### Domain model 
``` 
@Entity  
public class Person{    
     @Id   
     private String id = UUID.randomUUID().toString();   
     @FindBy   
     private String name;  
	 @FindAllBy     
	 private Integer age;   
     @FindBy @FindAllBy   
     private String address;   
     // getters & setters, etc..  
} 
``` 
As can be observed, a field can have both annotations at the same time.    
#### Example Service Layer
 ```
 @Service 
 public class PersonService{   
	@Autowired     
	private DataManager<Person> personDataManager;  
	 
	/* corresponds to @FindBy private String name;   
	returns a list of persons with the (same) given name */  
	public List<Person> getPersonsByName(String name){     
	return personDataManager.FindBy("name", name).orElse(...);}       
	  
	//corresponds to @FindAllBy private Integer age;   
	public List<Person> getAllPersonsByAge(List<Integer> ages){   
	 return personDataManager.FindAllBy("age", ages).orElse(...);}       

	//the following two methods correspond to @FindBy @FindAllBy private String address;    
	public List<Person> getPersonsByAddress(String address){   
	 return personDataManager.FindBy("address", address).orElse(...);}   
	  
	public List<Person> getAllPersonsByAddressIn(List<String> addresses){    
		return personDataManager.FindAllBy("address", addresses).orElse(...);}
}
```
#### Domain model 
```  
@Entity  
public class Person{    
     @Id   
     @GeneratedValue
     private Long id;   
     @FindBy   
     private String name;  
	 @FindAllBy     
	 private Integer age;   
     @FindBy @FindAllBy   
     private String address;   
     // getters & setters, etc..  
} 
``` 
As can be observed, a field can have both annotations at the same time.    
##### Example Service Layer
 ``` 
 @Service public class PersonService{   
 @Autowired     private DataManager<Person> personDataManager;   
       
	 /* corresponds to @FindBy private String name;   
	    returns a list of persons with the (same) given name */  
		public List<Person> getPersonsByName(String name){     
			 return personDataManager.FindBy("name", name).orElse(...);}       
       
	   //corresponds to @FindAllBy private Integer age;   
	 public List<Person> getAllPersonsByAge(List<Integer> ages){   
	      return personDataManager.FindAllBy("age", ages).orElse(...);}       
       
     //the following two methods correspond to @FindBy @FindAllBy private String address;    
     public List<Person> getPersonsByAddress(String address){   
        return personDataManager.FindBy("address", address).orElse(...);}    
       
     public List<Person> getAllPersonsByAddressIn(List<String> addresses){    
        return personDataManager.FindAllBy("address", addresses).orElse(...);}
 }
 ```  
## @FindByUnique  
As can be observed, the return type of both of the previous methods is a list. That's because there is no guarantee of uniqueness with regards to a field simply because it's been annotated with `@FindBy` and / or `@FindAllBy`. This is where `@FindByUnique` differs; it takes a unique value argument, and returns a single corresponding entity. In order for this to be valid syntax, any field annotated with the `@FindByUnique` annotation must also be annotated with `@Column(unique = true)`. If a field is annotated with only `@FindByUnique` but not `@Column(unique = true)`, a compilation error will occur. The following is an illustrative example:  
#### Domain model 
``` 
@Entity  
public class Person{   
	 @Id @GeneratedValue
	 private Long id;
	 @FindByUnique @Column(unique = true)
	 private String name;  
	 //...
} 
``` 
#### Example Service Layer 
``` 
@Service 
public class PersonService{   
	 @Autowired
	 private DataManager<Person> personDataManager;   
	 /*   
	 corresponds to   
	 @FindByUnique   
	 private String name;   
	 Returns a single person with the given name  
	*/     
	 public Person getPersonByUniqueName(String name){   
        return personDataManager.FindByUnique( "name", name).orElse(...);
     } 
}   
```  
  
## Free text search  
Datafi comes with non case sensitive free text ("Fuzzy") search out of the box. To make use of this, either one or more **String typed** fields can be annotated with `@FreeTextSearchBy`, or the class itself can be annotated with `@FreeTextSearchByFields({"field1", "field2", etc...})`.  Then the `freeTextSearchBy(String searchTerm, args...)` method in the respective class' `DataManager` can be called.   
  
Observe the following example:  
  
##### Domain model 
``` 
@Entity  
//@FreeTextSearchByFields({"name", "email"}) - this is equivalent to the field level annotations below  
public class Person{   
	 @Id @GeneratedValue
	 private Long id;   
	       
	@FreeTextSearchBy  
	private String name; 
	@FreeTextSearchBy private String email; 
	//...
} 
``` 
##### Example Service Layer 
``` 
@Service public class PersonService{
   
	 @Autowired
	 private DataManager<Person> personDataManager;   
	 
	 public List<Person> freeTextSearchPeople(String searchTerm){   
	        return personDataManager.freeTextSearch(searchTerm);   
	 } 
}   
```  
`freeTextSearch` does returns the listed contents of a `Page` object. This means that the search results are paginated by definition. Because of this, `freeTextSearch` takes in the 2 optional arguments `int offset` and `int limit` - in that order. These are "optional" in the sense that if not specified, the offset and limit will default to 0 and 50 respectively. An additional 2 optional arguments are `String sortBy` and `Sort.Direction sortDirection` - in that order. `String sortBy` specifies the name of a field within the given entity by which to apply the sort. If no matching field is found an `IllegalArgumentException` is thrown. `Sort.Direction sortDirection` determines the ordering strategy. If not specified it defaults to ascending order (`ASC`).  
       
### cascadedUpdate 
One issue which requires attention when designing a data model is cascading. Datafi simplifes this by offering out-of-the-box, built in application layer cascading when applying update operations. See illustration:  
```
@Service public class PersonService{   
	 @Autowired
	 private DataManager<Person> personDataManager;      
	       
	 public Person updatePerson(Person toUpdate, Person objectWithUpdatedValues){    
	    return personDataManager.cascadedUpdate(toUpdate,  objectWithUpdatedValues);  
	}  
} 
```    
 Breakdown:     
 - The first argument is the `Person` instance we wish to update.    
     
 - The second argument is an instance of `Person` containing the updated values to be assigned to the corresponding fields within the first `Person` instance.  All of the it's other fields **must** be null.     
     
    **Important note**: This method skips over any iterables.    
    
### cascadeUpdateCollection 
`cascadeUpdateCollection` offers analogous functionality to `cascadeUpdate`, only in plural. For Example:  
    
``` 
@Service public class PersonService{   
 @Autowired
 private DataManager<Person> personDataManager;   
       
     //obviously, these two lists must correspond in length   
     public List<Person> updatePersons(List<Person> toUpdate, List<Person> objectsWithUpdatedValues){   
        return personDataManager.cascadeUpdateCollection(toUpdate, objectsWithUpdatedValues);   
     }  
}
 ```    
 ### Excluding fields from cascadeUpdate(...) 
 operations Field(s) to be excluded from `cascadeUpdate` operations should be annotated as `@NonApiUpdatable`. Alternately, if there are many such fields in a class and the developer would rather avoid the field-level annotational clutter, the class itself can be annotated with `@NonApiUpdatables`, with the relevant field names passed as arguments. For example, the following:  
    
 ``` 
 @Entity    
 public class Person {   
   
     @Id   
     private String id = UUID.randomUUID().toString();  
      @NonApiUpdatable   
     private String name;   
       
     @NonApiUpdatable   
     private Integer age;   
       
     private String address;   
       
 }  
 ```  
 is equivalent to:    
    
 ```
  @Entity 
  @NonApiUpdatables({"name", "age"}) public class Person {   
	 @Id 
	 @GeneratedValue   
	 private Long id;   
	 private String name;   
	 private String address;   
 } 
 ``` 
 ### Mutating the state of foreign key Iterables 
 As metioned above, `cascadeUpdate` operations skip over iterable type fields. This is due to the fact that collection mutations involve adding or removing elements to or from the collection - not mutations on the collection container itself. Therefore, `DataManager<T>` includes the following methods to help with adding to, and removing from, foreign key collections.    
1. `public<HasTs> List<T> createAndAddNewToCollectionIn(HasTs toAddTo, String fieldName, List<T> toAdd)`    
 This method takes in three arguments, while making internal use of the application level `cascadedUpdate` above in order to propogate the relevant state changes:    
   - `HasTs toAddTo` - The entity containing the foriegn key collection of "Ts" (The type of entities referenced in the collection) to which to add.  
   - `String fieldName` - The field name of the foreign key collection (i.e. for `private Set<Person> friends;`, it'd be `"friends"`).   
   - `List<T> toAdd` - The entities to add to the collection.    
2. `public<HasTs> List<T> associateExistingWithCollectionIn(HasTs toAddTo, String fieldName, List<T> toAttach)` Similar to the previous method but for one crucial difference; it ensures the entities to be _attached_ (**not added** from scratch) are indeed **already present** within their respective table within the database.  
    
## Extras
### StandardPersistableEntity  
If you don't wan't to worry about assigning `@Id` or `@Version` column. the `StandardPersistableEntity` `@MappedSuperclass` can be extended. For example:    
#### Domain model
 ``` 
 @Entity 
 public class Person extends StandardPersistableEntity {    
    private String name;   
    private Integer age;   
    // getters & setters, etc...  
} 
```    
 ### IdFactory  
Alternately, the unix timestamp based `Long IdFactory.getNextId()` static method can be employed. For example:    
    
``` @Entity public class Person{    
     @Id   
     private Long id = IdFactory.getNextId();   
     private String name;   
     private Integer age;   
     // getters & setters, etc...  
} 
```   
    
That's all for now, happy coding! 
## License  
 [Apache 2.0](https://github.com/sanda-dev/datafi/blob/master/LICENSE)