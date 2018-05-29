#### Surrounding Scanner

pdesai4@binghamton.edu

It can be difficult for a visually disabled person to know distant objects present in their surroundings. This app helps them to know the same and create an image of the surrounding in their minds to help them see in their own unique way. This is an app made in Android which scans the surrounding using the smartphones back camera and recognizes the objects and speaks them out loud. To analyze the objects, we use Google’s Cloud Platform Vision API for image analysis and Android’s Text-to-Speech service to speak out loud to the user.  



**Requirements** 

Minimum SDK version: 16 Target SDK version: 27 The app requires camera and external storage permission and it asks for the permission during the first use. 

The compute machine instance over the [Google Cloud Platform Vision API](https://cloud.google.com/vision/) has 1 vCPU and 1.7 GB memory. 

The API key for the Cloud compute engine needs to be put in the gradle.properties file in the format “`VISION_API_KEY=api-key`”. 

This app uses the open source [Okhttp](http://square.github.io/okhttp/) tool to perform API calls over the network in JSON. 



**Permissions** 

The app requires permission to use the camera resource and the external storage to save the images to a temporary cache storage. Internet permission is also required to make API calls. The app asks for these permissions from the user when the user uses the app for the first time. Along with the camera hardware feature, the app also uses auto-focus feature. 