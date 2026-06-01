pipeline {
    agent any
    
    options {
        quietPeriod(120)
        buildDiscarder(logRotator(daysToKeepStr: '40', numToKeepStr: '70'))
    }
    
    triggers {
        githubPush()
    }
    
    parameters {
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
        stage('Checkout') {
            steps {
                checkout scm
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
            office365ConnectorSend webhookUrl: "${TEAMS_WEBHOOK}", status: 'Failed'
        }
        
        unstable {
            office365ConnectorSend webhookUrl: "${TEAMS_WEBHOOK}", status: 'Unstable'
            build job: 'build', wait: false
        }
        
        fixed {
            office365ConnectorSend webhookUrl: "${TEAMS_WEBHOOK}", status: 'Back to Normal'
        }
        
        success {
            build job: 'build', wait: false
        }
    }
}