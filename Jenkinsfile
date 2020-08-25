

buildMvn {
  publishModDescriptor = 'no'
  publishAPI = 'yes'
  mvnDeploy = 'yes'
  runLintRamlCop = 'yes'
  buildNode = 'jenkins-agent-java11'

  doDocker = {
    buildJavaDocker {
      dockerDir = 'okapi-core'
      overrideConfig  = 'no'
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'curl --fail http://localhost:9130/_/proxy/health || exit 1'
      runArgs = 'dev'
    }
  }
}

