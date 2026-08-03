pipeline {
    agent any
    
    options {
        quietPeriod(120)
        buildDiscarder(logRotator(daysToKeepStr: '40', numToKeepStr: '70'))
    }
    
   triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ref', value: '$.ref']
            ],
            token: 'sablo',
            regexpFilterText: '$ref',
            regexpFilterExpression: "^refs/heads/${env.BRANCH}\$"
        )
    }
    
    
    parameters {
        // New boolean toggle for manual workspace wiping
        booleanParam(name: 'WIPE_WORKSPACE', defaultValue: false, description: 'Check this box to completely wipe the workspace BEFORE running the build.')

        string(name: 'goals', defaultValue: 'clean install', trim: false)
    }
    
    environment {
        // Haal de webhook URL veilig op uit de Jenkins kluis
        TEAMS_WEBHOOK = credentials('servoy-teams-webhook')
    }
    
    tools {
        jdk 'Java 21'
        maven 'Maven 3.9.16'
    }
    
    stages {
        stage('Clear Queued Builds') {
            steps {
                script {
                    // Annuleer builds die in de queue wachten op de quietPeriod timer voor EXPANCT dit specifieke pad (bijv. "lts_2026/servoy-eclipse")
                    def currentJob = env.JOB_NAME
                    def queue = jenkins.model.Jenkins.get().queue
                    
                    queue.items.each { item ->
                        // ownerTask.fullName works for boht WorkflowJob or  PlaceholderTask objects
                        def queuedJobName = item.task.ownerTask?.fullName
                        if (queuedJobName == currentJob) {
                            echo "Removing pending queued build for ${currentJob} (Queue ID #${item.id})..."
                            queue.cancel(item)
                        }
                    }
                }
            }
        }

         // This stage executes first, but only if you checked the box in the UI
        stage('Manual UI Workspace Wipe') {
            when {
                expression { params.WIPE_WORKSPACE }
            }
            steps {
                echo "âš ï¸� Manual workspace wipe requested via UI toggle. Cleaning up..."
                cleanWs()
            }
        }

        stage('Build with Tycho 5') {
            steps {
                configFileProvider([
                    configFile(fileId: 'master_mvn_repo', variable: 'MAVEN_SETTINGS'),
                    configFile(fileId: 'maven_toolchain', variable: 'TOOLCHAIN')
                ]) {
                    // Bouwstap met de juiste submap 'sablo/pom.xml' en de dynamische $goals
                    sh 'mvn -B -f sablo/pom.xml -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" $goals'
                }
            }
        }
    }
    
    post {
        always {
            // Testrapportage voor Sablo
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        }
        
        failure {
           office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Failed'
        }
        
        unstable {
           office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Unstable'
            build job: 'build', wait: false
        }
        
        fixed {
           office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Back to Normal'
        }
        
        success {
            build job: 'build', wait: false
        }
    }
}