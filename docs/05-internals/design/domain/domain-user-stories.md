# Domain User Stories

The following section provides a list of use cases / user stories. 
They should ideally cover all cases of what a user may want to do when they interact with the DSP.

!!! info "Legend"

    The different bullet points mean:

    - [ ] Not yet possible
    - [x] Fully implemented
    - partially implemented / needs checking

  - Administration
      - User Administration
          - Join the DSP as a user
            Currently no self-sign-up, only system admins can add users.
          - [x] Add team members to a project
          - [x] Make a user a project administrator
          - [x] Remove team members from project
          - [x] Revoke the project administration role from a user
          - Create groups with certain permissions attached
          - [x] Modify permissions for a group
          - [x] Add project members to groups in order to give users particular permissions
          - [x] Remove project members from groups
          - [x] Make a user a system administrator
          - [x] Revoke the system administrator role from a user
          - [x] Update information about a user
          - [x] Inactivate a user
      - Project Administration
          - Create a project  
            Only possible for system administrators.
          - [x] Delete a project
          - [x] Define default object access permissions for the project / group
          - [x] Define administrative permissions for the project / group
          - [x] Update permissions
          - Update Project metadata
              - [x] in DSP
              - in META  
                Only possible through the DSP-META repo
  - Data modeling
      - [x] Add one or more data models (ontologies) to a project
      - [x] Update ontology metadata
      - [x] Add resource classes to a datamodel
      - [x] Edit resource classes
      - [x] Delete resource classes
      - [x] Add (reusable) properties to a datamodel
      - [x] Edit properties
      - [x] Delete properties
      - [x] Add properties to a resource class with a defined cardinality
      - [x] Remove properties from a resource class
      - Change the cardinality of a property on a resource class
      - [x] Add a list to a project
      - [x] Edit a list
      - [x] Delete a list
  - Data generation
      - [x] Creating resources
      - Updating resources
          - Changing the resource type
          - [x] Adding values
          - [x] Updating values
          - Annotating/commenting on values
          - [x] Deleting values
      - Linking resources
          - [x] to other project resources
          - to external resources
      - Annotating/commenting on resources
      - [x] Deleting resources
      - [x] Erasing resources
    - [ ] Data archiving
        - [ ] Publish the existing data  
          Only possible through changing permissions (which app doesn't allow)
        - [ ] Version citable resources
  - Data reuse
      - Browsing
          - [x] Browse projects
          - [x] Browse project metadata
          - Inspect a project's data model(s)
          - Browse a project's data
              - [ ] all data
              - [x] by resource class
              - [ ] matching filters/facettes
      - [x] Searching  
        With the caveat that certain searches that are possible may not be good, fast or intuitive
          - [x] Search for projects covering a certain topic
          - [ ] Search for projects that have a certain keyword
          - [x] Search for a project of which one knows it already exists
          - [x] Search for a datapoint of which one already knows it exists
          - [x] Search for data matching criteria within a project
          - [x] Search for data matching criteria across projects
      - Programmatic reuse
          - [ ] download datasets or part(s) of it in different formats
          - [x] retrieve data matching certain search/filter criteria
          - [x] retrieve single resources/values by identifiers
