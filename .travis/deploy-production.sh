# !/bin/bash
#
# Deploying to production environment

echo "export TEXTUP_BACKEND_AWS_ACCESS_KEY=\"${PROD_AWS_ACCESS_KEY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_AWS_SECRET_KEY=\"${PROD_AWS_SECRET_KEY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_CDN_KEY_ID=\"${PROD_CDN_KEY_ID}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_DB_PASSWORD=\"${PROD_DB_PASSWORD}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_DB_USERNAME=\"${PROD_DB_USERNAME}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_EXEC_USERNAME=\"${PROD_EXEC_USERNAME}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_FFMPEG_COMMAND=\"${PROD_FFMPEG_COMMAND}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_FFMPEG_DIRECTORY=\"${PROD_FFMPEG_DIRECTORY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_PUSHER_API_KEY=\"${PROD_PUSHER_API_KEY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_PUSHER_API_SECRET=\"${PROD_PUSHER_API_SECRET}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_SENDGRID_API_KEY=\"${PROD_SENDGRID_API_KEY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_TEMP_DIRECTORY=\"${PROD_TEMP_DIRECTORY}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_TWILIO_AUTH=\"${PROD_TWILIO_AUTH}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_TWILIO_NUMBER_APP_ID=\"${PROD_TWILIO_NUMBER_APP_ID}\"" >> .travis/env-variables/production.sh
echo "export TEXTUP_BACKEND_TWILIO_SID=\"${PROD_TWILIO_SID}\"" >> .travis/env-variables/production.sh
.travis/scripts/set-up-ffmpeg.sh $DEPLOY_USER_PRODUCTION $DEPLOY_HOSTNAME_PRODUCTION .travis/textup.pem $PROD_EXEC_USERNAME $PROD_FFMPEG_DIRECTORY $PROD_FFMPEG_COMMAND
.travis/scripts/set-up-temp-directory.sh $DEPLOY_USER_PRODUCTION $DEPLOY_HOSTNAME_PRODUCTION .travis/textup.pem $PROD_EXEC_USERNAME $PROD_TEMP_DIRECTORY
.travis/scripts/build-env-variables.sh $DEPLOY_USER_PRODUCTION $DEPLOY_HOSTNAME_PRODUCTION .travis/textup.pem .travis/env-variables/production.sh
.travis/scripts/deploy-war.sh $DEPLOY_USER_PRODUCTION $DEPLOY_HOSTNAME_PRODUCTION .travis/textup.pem target textup-backend.war
