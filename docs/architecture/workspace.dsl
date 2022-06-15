workspace "Architecture Diagrams for DSP" "This is a collection of diagrams for the DSP architecture" {
    # https://github.com/structurizr/dsl/blob/master/docs/language-reference.md
    # for an example, see https://structurizr.com/dsl?example=big-bank-plc

    

    # static model
    model {
        !include users.dsl
        
        enterprise "DSP - DaSCH Service Platform" {
            # softwareSystem <name> [description] [tags]
            dspJsLib        = softwaresystem "JS-LIB"               "Layer between DSP-API and DSP-APP"
            dspApp          = softwaresystem "DSP-APP"              "admin.dasch.swiss"
            dspTools        = softwaresystem "DSP-TOOLS"            "CLI for DSP-API"
            fuseki          = softwaresystem "Fuseki Triplestore"   "RDF database"                          "Database"
            sipi            = softwaresystem "SIPI"                 "IIIF image server"
            arkResolver     = softwaresystem "ARK Resolver"         "Forwards ARK URLs to DSP-APP URLs"
            dspApi          = softwareSystem "DSP-API"   "api.dasch.swiss" {
                # container <name> [description] [technology] [tags]
                userSlice       = container "User Slice" "The slice that handles users" {
                    userCore        = component "User Core"
                    userRepo        = component "User Repo"
                    userDomain      = component "User Domain"
                    userHandler     = component "User Handler"
                    userRepoLive    = component "User Repo Live Implementation"
                    userRepoMock    = component "User Repo Mock Implementation for Tests"
                    userRoute       = component "User Route"
                }
                eventStoreService = container "Event Store Service"
                eventListener     = container "Event Listener"
                projectSlice      = container "Project Slice" "The slice that handles projects"
                roleSlice         = container "Role Slice" "The slice that handles roles"
            }
        }

        # relationships between people and software systems
        user             -> dspApp       "Uses [Browser]"
        user             -> dspTools     "Uses [CLI]"

        # relationships to/from software systems
        dspApp      -> dspJsLib
        dspJsLib    -> dspApi
        dspTools    -> dspApi
        dspApi      -> fuseki 
        dspApi      -> sipi
        dspTools    -> sipi
        
        # relationships to/from containers

        # relationships to/from components
        userHandler -> userDomain "depends on"
        userRepo -> userDomain "depends on"
        userRepoLive -> userDomain "depends on"
        userRepoMock -> userDomain "depends on"
        userRoute -> userDomain "depends on"
    }

    views {
        systemlandscape "SystemLandscape" "System Landscape of DSP-API" {
            include *
            autoLayout
        }

        # systemContext <software system identifier> [key] [description]
        systemContext dspApi "SystemContextDspApi" "DSP-API System Context" {
            include *
            autoLayout
        }

        # container <software system identifier> [key] [description]
        container dspApi "ContainerDspApi" "Containers of DSP-API" {
            include *
            autoLayout
        }

        # component <container identifier> [key] [description]
        component userSlice "ComponentUserSlice" "User Slice" {
            include *
            autoLayout
        }

        # dynamic <*|software system identifier|container identifier> [key] [description]
        dynamic userSlice "AB" {
            user -> userRoute "sends HTTP request to"
            userRoute -> userRoute "validates input and creates value objects"
            userRoute -> userHandler "createUser(vo)"
            userHandler -> userRepo "reserve username"
            userRepo -> eventStoreService "reserve username"
            eventStoreService -> eventStoreService "check if username exists"
            eventStoreService -> eventStoreService "reserve username"
            autoLayout
        }

        theme default
    }

    !adrs decisions
    !docs docs
}
