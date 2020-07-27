# chatroom

Simple chatroom application using HBase as a data store.

## Local setup

You can run the application in your local kubernetes cluster.
In order to run it you would need:
*  docker, on OS X
you can do that by using Homebrew:
`brew cask install docker`. Afterwards, enable the local kubernetes cluster in the 
docker preferences and make sure the local cluster is selected

* skaffold: 
`brew install skaffold`

Once you have installed both, you can start the application by running the following command in the
project root directory:

`skaffold dev --port-forward=true --no-prune=false --cache-artifacts=false`

This command would run the application in your local k8s cluster. When you terminate
skaffold, it would clean up all k8s resources used by the application.
All kubernetes resources are described in the `/k8s` directory.
Pay attention to the ports defined in the service object, those will be forwarded to the same numbered ports
on your localhost, if those ports are available. If not, random free ports on your locahost would be used.

## Api
See the included [Postman](https://www.postman.com/) collection for the possible HTTP requests
