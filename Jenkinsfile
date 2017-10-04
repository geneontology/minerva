pipeline {
    
    agent any
    
    stages {
	
	// stage('Checkout'){
	//     steps {
	// 	checkout scm
	//     }
	// }
	
	stage('Check'){
	    steps {
		sh "mvn -v"
		sh "java -version"
	    }
	}
	
	stage('Test'){
	    steps {
		//sh "mvn test"
		sh "mvn clean package -am -pl minerva-cli -DskipTests -Dmaven.javadoc.skip=true -Dsource.skip=true"
	    }
	}
	
	// stage 'package'
	// sh "mvn package"
	
	// stage 'report'
	// step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
	
	// stage 'Artifact'
	// step([$class: 'ArtifactArchiver', artifacts: '**/target/*.jar', fingerprint: true])
    }
    
    // TODO: Let's make an announcement if things go badly.
    post { 
        changed { 
            echo 'There has been a change in the pipeline.'
        }
    }
}
