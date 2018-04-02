# TextUp Backend

TextUp Grails backend

| Service | Master | Dev |
| --- | --- | --- |
| CI Status | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=master)](https://travis-ci.org/TextUp/textup-backend) | [![Build Status](https://travis-ci.org/TextUp/textup-backend.svg?branch=dev)](https://travis-ci.org/TextUp/textup-backend) |

## Installation

### Using Grails wrapper

* Make sure the `grailsw` is executable: `chmod +x grailsw`
* For all following commands, replace `grails` with `./grailsw`

### Using SDKMAN!

* [Install SDKMAN!](http://sdkman.io/install.html)
* `sdk install grails 2.4.4`

## Databases

Both development and testing environments use an in-memory H2 database.

Staging and production environments use a MySQL v5.6 database. In order for the app to successfully start up, the appropriate user must be created with full permissions on the production database. Check `DataSource.groovy` to see the name of the production database.

Make sure to pass in the correct username and password values for the database user you create with access to the production database. See the following section on environment variables for the names of the properties you may use to pass in the database username and password.

## Environment variables

In order to successfully run, certain environment variables are required, accessed primarily in `grails-app/conf/BuildConfig.groovy`. Below, we outline variables required for each environment with typical values where appropriate.

**NOTE**: if you are changing `ENABLE_QUARTZ` to true on the staging or development environments, make sure to double check that Quartz has no triggers that will fire. Triggers that fire immediately when past due will cause all those outdated scheduled messages to erroneously send to possibly many unintended recipients.

### For all environments: JavaMelody monitoring

Storage directory must be hardcoded in `grails-app/conf/GrailsMelodyConfig.groovy` because this value fixed when the WAR file is built. **Therefore, the app requires the folder `/grails-monitoring`.**

Make sure user and group for this folder are both `tomcat7`: `sudo chown -R tomcat7:tomcat7 /grails-monitoring`

### Development (local)

* `AWS_ACCESS_KEY`: secure token
* `AWS_SECRET_KEY`: secure token
* `CDN_KEY_ID`: secure token
* `CDN_PRIVATE_KEY_PATH`: usually `/cloudfront-2017-private.der`
* `CDN_ROOT`: usually `staging-media.textup.org`
* **`ENABLE_QUARTZ`: should be `false`**
* `PUSHER_API_KEY`: secure token
* `PUSHER_API_SECRET`: secure token
* `RECAPTCHA_SECRET`: secure token
* `SENDGRID_API_KEY`: secure token
* `STORAGE_BUCKET_NAME`: usually `staging-media-textup-org`
* `TWILIO_AUTH`: secure token, should be **test credential**
* `TWILIO_NOTIFICATIONS_NUMBER`: usually `+14012878632`
* `TWILIO_NUMBER_APP_ID`: secure token
* `TWILIO_SID`: secure token, should be **test credential**
* `TWILIO_TEST_AUTH`: secure token, should be **test credential**
* `TWILIO_TEST_SID`: secure token, should be **test credential**
* `URL_ADMIN_DASHBOARD`: usually `http://demo.textup.org/#/admin`
* `URL_NOTIFY_MESSAGE`: usually `http://demo.textup.org/#/notify?token=`
* `URL_PASSWORD_RESET`: usually `http://demo.textup.org/#/reset?token=`
* `URL_SETUP_ACCOUNT`: usually `http://demo.textup.org/#/setup`
* `URL_SUPER_DASHBOARD`: usually `https://dev.textup.org/super`

### [Travis CI](https://travis-ci.org/TextUp/textup-backend) (testing and deployment)

Travis uses the same values as the development environment except for the following additions and modifications:

* `CDN_PRIVATE_KEY_PATH`: should be `$TRAVIS_BUILD_DIR/.travis/test.pem`
* `HOSTNAME_PRODUCTION`: should be `https://v2.textup.org`
* `HOSTNAME_STAGING`: should be `https://dev.textup.org`
* `USER_PRODUCTION`: username of production machine
* `USER_STAGING`: username of staging machine

### [Staging](https://dev.textup.org)

* `AWS_ACCESS_KEY`: secure token
* `AWS_SECRET_KEY`: secure token
* `CDN_KEY_ID`: secure token
* `CDN_PRIVATE_KEY_PATH`: usually `/cloudfront-2017-private.der`
* `CDN_ROOT`: usually `staging-media.textup.org`
* **`DB_PASSWORD`: secure value**
* **`DB_USERNAME`: secure value**
* **`ENABLE_QUARTZ`: should be `true`**
* `PUSHER_API_KEY`: secure token
* `PUSHER_API_SECRET`: secure token
* `RECAPTCHA_SECRET`: secure token
* `SENDGRID_API_KEY`: secure token
* **`SERVER_URL`: usually `https://dev.textup.org`**
* `STORAGE_BUCKET_NAME`: usually `staging-media-textup-org`
* `TWILIO_AUTH`: secure token, can be either test or production credentials
* `TWILIO_NOTIFICATIONS_NUMBER`: usually `+14012878632`
* `TWILIO_NUMBER_APP_ID`: secure token
* `TWILIO_SID`: secure token, can be either test or production credentials
* `URL_ADMIN_DASHBOARD`: usually `http://demo.textup.org/#/admin`
* `URL_NOTIFY_MESSAGE`: usually `http://demo.textup.org/#/notify?token=`
* `URL_PASSWORD_RESET`: usually `http://demo.textup.org/#/reset?token=`
* `URL_SETUP_ACCOUNT`: usually `http://demo.textup.org/#/setup`
* `URL_SUPER_DASHBOARD`: usually `https://dev.textup.org/super`

### [Production](https://v2.textup.org)

* `AWS_ACCESS_KEY`: secure token
* `AWS_SECRET_KEY`: secure token
* `CDN_KEY_ID`: secure token
* `CDN_PRIVATE_KEY_PATH`: usually `/cloudfront-2017-private.der`
* `CDN_ROOT`: usually `media.textup.org`
* **`DB_PASSWORD`: secure value**
* **`DB_USERNAME`: secure value**
* **`ENABLE_QUARTZ`: should be `true`**
* `PUSHER_API_KEY`: secure token
* `PUSHER_API_SECRET`: secure token
* `RECAPTCHA_SECRET`: secure token
* `SENDGRID_API_KEY`: secure token
* **`SERVER_URL`: usually `https://v2.textup.org`**
* `STORAGE_BUCKET_NAME`: usually `media-textup-org`
* `TWILIO_AUTH`: secure token, should be **production credential**
* `TWILIO_NOTIFICATIONS_NUMBER`: usually `+14012878632`
* `TWILIO_NUMBER_APP_ID`: secure token
* `TWILIO_SID`: secure token, should be **production credential**
* `URL_ADMIN_DASHBOARD`: usually `https://app.textup.org/#/admin`
* `URL_NOTIFY_MESSAGE`: usually `https://app.textup.org/#/notify?token=`
* `URL_PASSWORD_RESET`: usually `https://app.textup.org/#/reset?token=`
* `URL_SETUP_ACCOUNT`: usually `https://app.textup.org/#/setup`
* `URL_SUPER_DASHBOARD`: usually `https://v2.textup.org/super`

## Running / Development

### Development

* `grails run-app`

### Running Tests

Running the entire test suite may lead to an insufficient PermGen space error. Therefore, we split up running the test suite to avoid this issue.

See the `script` section of `.travis.yml` for the tests. Note that the command is `grails` instead of `./grailsw` if you installed via SDKMAN! and are not using the wrapper.

### Building for deployment

* `grails prod war`

### Deploying

#### Deploying to staging

* Push to the `dev` branch to trigger a Travis CI build OR
* Deploy manually
    * Copy the generated WAR file to the appropriate server using `scp`
    * Copy the generated WAR file to the appropriate location on the server and restart the Tomcat server. See `.travis/remote.sh` for the steps.

#### Deploying to production

* Push to the `master` branch to trigger a Travis CI build OR
* Deploy manually (same steps as for the staging environment)

## Useful links

* [Grails 2.4.4 documentation](https://grails.github.io/grails2-doc/2.4.4/index.html)

## License

Copyright 2018 TextUp

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
