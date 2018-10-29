/*
   Copyright 2013 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.senders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.BucketNameUtils;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.ParameterException;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.core.SenderWithParametersBase;
import nl.nn.adapterframework.core.TimeOutException;
import nl.nn.adapterframework.parameters.ParameterList;
import nl.nn.adapterframework.parameters.ParameterResolutionContext;
import nl.nn.adapterframework.parameters.ParameterValueList;
import nl.nn.adapterframework.util.Misc;

/**
 * <p>
 * S3Sender, makes possible for Ibis developer to interact with Amazon Simple Storage Service (Amazon S3). It allows to make
 * and remove buckets. More so it makes possible for you to upload object(s) inside a bucket, delete object(s) from a bucket and 
 * copy object(s) from one bucket too another.
 * </p>
 * 
 * <p>
 * <b>Configuration:</b>
 * <table border="1">
 * <tr><th>attributes</th><th>description</th><th>default</th></tr>
 * <tr><td>{@link #setName(String) name}</td><td>Set a name for your sender</td><td></td></tr>
 * <tr><td>{@link #setChunkedEncodingDisabled(boolean) chunkedEncodingDisabled}</td><td>Configures the client to disable chunked encoding for all requests.</td><td>false</td></tr>
 * <tr><td>{@link #setAccelerateModeEnabled(boolean) accelerateModeEnabled}</td><td>Configures the client to use S3 accelerate endpoint for all requests. A bucket by default cannot be accessed in accelerate mode. If you wish to do so, you need to enable the accelerate configuration for the bucket in advance. (This includes extra costs)</td><td>false</td></tr>
 * <tr><td>{@link #setForceGlobalBucketAccessEnabled(boolean) forceGlobalBucketAccessEnabled}</td><td>Configure whether global bucket access is enabled for this client. When enabled client in a specified region is allowed to create buckets in other regions.</td><td>false</td></tr>
 * <tr><td>{@link #setBucketCreationEnabled(boolean) bucketCreationEnabled}</td><td>When uploading/copying an object to a non existent bucket, this attribute must be set to 'true' which allows the creation of the new bucket. Otherwise an exception will be thrown.</td><td>false</td></tr>
 * <tr><td>{@link #setActions(String) actions}</td><td>Available actions are (separated by comma actions can be used together, actions can only be performed on one bucket and object):
 * <ul><li>mkBucket: create a new bucket inside Amazon S3</li>
 * <li>rmBucket: delete an existing bucket from S3</li>
 * <li>upload: puts an object inside a S3 bucket (file parameter required)</li>
 * <li>download: gets an object from a S3 bucket for further and safes it in ???</li>
 * <li>copy: copies an object from one S3 bucket to another S3 bucket (destinationBucketName and destinationObjectKey parameter required)</li>
 * <li>delete: delete an object from inside a S3 bucket</li></ul></td><td></td></tr>
 * <tr><td>{@link #setClientRegion(String) clientRegion}</td><td>Set a region end point for creation of this client. Available regions are: 
 * <ul><li>us-gov-west-1, us-east-1, us-east-2, us-west-1, us-west-2</li>
 * <li>eu-west-1, eu-west-2, eu-west-3, eu-central-1</li>
 * <li>ap-south-1, ap-southeast-1, ap-southeast-2, ap-northeast-1, ap-northeast-2</li>
 * <li>sa-east-1, cn-north-1, cn-northwest-1, ca-central-1</li></ul></td><td>"eu-central-1"</td></tr>
 * <tr><td>{@link #setBucketName(String) bucketName}</td><td>Set a new or an existing name for S3 bucket depending on your actions</td><td></td></tr>
 * <tr><td>{@link #setObjectKey(String) objectKey}</td><td>Set a new or an existing name for your object depending on your actions</td><td></td></tr>
 * </table>
 * </p>
 * 
 * <p>
 * <b>Parameters:</b>
 * <table border="1">
 * <tr><th>name</th><th>type</th><th>remarks</th></tr>
 * <tr><td>objectKey</td><td><i>String</i></td><td>When a parameter with name objectKey is present, it is used instead of the message for a name of an object</td></tr>
 * <tr><td>file</td><td><i>Stream</i></td><td>This parameter contains InputStream, it must be present when performing upload action</td></tr>
 * <tr><td>destinationBucketName</td><td><i>String</i></td><td>This parameter specifies the name of destination bucket, it must be present when performing copyObject action</td></tr>
 * <tr><td>destinationObjectKey</td><td><i>String</i></td><td>This parameter specifies the name of the copied object, it must be present when performing copyObject action</td></tr>
 * </table>
 * </p>
 * 
 * <p>
 * This is a list containing configurations that exist in AmazonS3 API and are not used in this sender:
 * <ul>
 * <li>bucket analytics</li>
 * <li>bucket cross origin configuration</li>
 * <li>bucket encryption</li>
 * <li>bucket inventory configuration</li>
 * <li>bucket lifecycle configuration</li>
 * <li>bucket metric configuration</li>
 * <li>bucket policy</li>
 * <li>bucket replication configuration</li>
 * <li>bucket tagging configuration</li>
 * <li>bucket website configuration</li>
 * <li>bucket requester configuration</li>
 * </ul>
 * </p>
 * 
 * @author R. Karajev
 * @author Robert Karajev
 */

public class S3Sender extends SenderWithParametersBase
{
	private AmazonS3ClientBuilder s3ClientBuilder;
	private AmazonS3 s3Client;
	private List<String> availableRegions = getAvailableRegions();
	private List<String> availableActions = Arrays.asList("mkBucket", "rmBucket", "upload", "download", "copy", "delete");
	
	private String name;
	private boolean chunkedEncodingDisabled = false;
	private boolean accelerateModeEnabled = false; // this may involve some extra costs
	private boolean forceGlobalBucketAccessEnabled = false;
	private boolean bucketCreationEnabled = false;
	private String clientRegion = Regions.EU_CENTRAL_1.getName();
	private String bucketRegion;
	private String actions;
	private String bucketName;


	@Override
	public void configure() throws ConfigurationException
	{
		super.configure();
		if(StringUtils.isEmpty(getClientRegion()) || !availableRegions.contains(getClientRegion()))
			throw new ConfigurationException(getLogPrefix() + " region unknown or is not specified [" + getClientRegion() + "] please use following supported regions: " + availableRegions.toString());
		
		StringTokenizer tokenizer = new StringTokenizer(getActions(), " ,\t\n\r\f");
		while (tokenizer.hasMoreTokens()) 
		{
			String action = tokenizer.nextToken();

	    	if(StringUtils.isEmpty(action) || !availableActions.contains(action))
				throw new ConfigurationException(getLogPrefix()+" action unknown or is not specified [" + action + "] please use following supported actions: " + availableActions.toString());	
			
			if(StringUtils.isEmpty(getBucketName()) || !BucketNameUtils.isValidV2BucketName(getBucketName()))
				throw new ConfigurationException(getLogPrefix() + " bucketName not specified or correct bucket naming is not used, visit AWS to see correct bucket naming");

			ParameterList parameterList = getParameterList();
			if(!(action.equalsIgnoreCase("mkBucket") || action.equalsIgnoreCase("rmBucket")))
			{				
				if(action.equalsIgnoreCase("upload") && parameterList.findParameter("file") == null)
					throw new ConfigurationException(getLogPrefix()+" file parameter requires to be present to perform [" + action + "]");
			
				if(action.equalsIgnoreCase("copy") && parameterList.findParameter("destinationBucketName") == null && parameterList.findParameter("destinnationObjectKey") == null)
					throw new ConfigurationException(getLogPrefix()+" destinationBucketName parameter requires to be present to perform [" + action + "] for copying an object to another bucket");
			}
	    }
				
		s3ClientBuilder = AmazonS3ClientBuilder.standard()
											.withChunkedEncodingDisabled(isChunkedEncodingDisabled())
                            				.withAccelerateModeEnabled(isAccelerateModeEnabled())
                            				.withForceGlobalBucketAccessEnabled(isForceGlobalBucketAccessEnabled())
                            				.withRegion(getClientRegion())
                            				.withCredentials(new EnvironmentVariableCredentialsProvider());
		
	}

	public void open()
	{
		s3Client = s3ClientBuilder.build();
	}

	public void close()
	{
		s3Client.shutdown();
	}

	public String sendMessage(String correlationID, String message, ParameterResolutionContext prc) throws SenderException, TimeOutException
	{
		//fills ParameterValueList pvl with the set parameters from S3Sender
		ParameterValueList pvl = null;
		try
		{
			if (prc != null && paramList != null)
				pvl = prc.getValues(paramList);
		} 
		catch (ParameterException e)
		{
			throw new SenderException(getLogPrefix() + "Sender [" + getName() + "] caught exception evaluating parameters", e);
		}
		
		String generalObjectKey = pvl.getParameterValue("objectKey").asStringValue(message);
		StringTokenizer tokenizer = new StringTokenizer(getActions(), " ,\t\n\r\f");
		while (tokenizer.hasMoreTokens()) 
		{
			String action = tokenizer.nextToken();
			if(action.equalsIgnoreCase("mkBucket"))
			{
				boolean bucketExistsThrowsException = true;
				createBucket(getBucketName(), bucketExistsThrowsException);
			}
			else if(action.equalsIgnoreCase("rmBucket"))
				deleteBucket(getBucketName());
			else if(action.equalsIgnoreCase("upload"))
				if(StringUtils.isNotEmpty(generalObjectKey))
					if(pvl.getParameterValue("file") != null)
						if(pvl.getParameterValue("file").getValue() != null)
							uploadObject(getBucketName(), generalObjectKey, pvl);
						else
							throw new SenderException(getLogPrefix() + " no value was assinged for file parameter");
					else
						throw new SenderException(getLogPrefix() + " file parameter doesn't exist, please use file parameter to perform [upload] action");
				else
					throw new SenderException(getLogPrefix() + " no value found for the name of objectKey, please assing a value");
			else if(action.equalsIgnoreCase("download"))
				if(StringUtils.isNotEmpty(generalObjectKey))
					downloadObject(getBucketName(), generalObjectKey);
				else
					throw new SenderException(getLogPrefix() + " no value found for the name of objectKey, please assing a value");
			else if(action.equalsIgnoreCase("copy"))
				if(StringUtils.isNotEmpty(generalObjectKey))
					if(pvl.getParameterValue("destinationBucketName") != null && pvl.getParameterValue("destinationObjectKey") != null)
						if(pvl.getParameterValue("destinationBucketName").getValue() != null && pvl.getParameterValue("destinationObjectKey").getValue() != null)
							copyObject(getBucketName(), generalObjectKey, pvl);
						else
							throw new SenderException(getLogPrefix() + " no value in destinationBucketName and/or destinationObjectKey parameter found, please assing values to the parameters to perfom [copy] action");
					else
						throw new SenderException(getLogPrefix() + " no destinationBucketName and/or destinationObjectKey parameter found, they must be used to perform [copy] action");
				else
					throw new SenderException(getLogPrefix() + " no value found for the name of objectKey, please assing a value");
			else if(action.equalsIgnoreCase("delete"))
				if(StringUtils.isNotEmpty(generalObjectKey))
					deleteObject(getBucketName(), generalObjectKey);
				else
					throw new SenderException(getLogPrefix() + " no value found for the name of objectKey, please assing a value");
	    }
		
		return message;
	}
	
	
	//DONE! creates a bucket (throwsException == true, then this method throws exception if bucket exists)
	public void createBucket(String bucketName, boolean bucketExistsThrowsException) throws SenderException
	{
		if(!s3Client.doesBucketExistV2(bucketName))
		{
			CreateBucketRequest createBucketRequest = null;
			if(isForceGlobalBucketAccessEnabled())
				if(StringUtils.isNotEmpty(getBucketRegion()) && availableRegions.contains(getBucketRegion()))
					createBucketRequest = new CreateBucketRequest(bucketName, getBucketRegion());
				else
					throw new SenderException(getLogPrefix() + " bucketRegion is unknown or not specified [" + getBucketRegion() + "] please use one of the following supported bucketRegions: " + availableRegions.toString());
			else
				createBucketRequest = new CreateBucketRequest(bucketName);			
			s3Client.createBucket(createBucketRequest);
System.out.println("Bucket ["+bucketName+"] is created.");
		}
		else
			if(bucketExistsThrowsException)
				throw new SenderException(getLogPrefix() + " bucket with bucketName: "+bucketName+" already exists, please specify a unique bucketName");
	}
	
	//DONE! deletes a bucket
	public void deleteBucket(String bucketName) throws SenderException
	{
		checkBucketAbsence(bucketName); //throws exception if bucket is absent
		DeleteBucketRequest deleteBucketRequest = new DeleteBucketRequest(bucketName);
		s3Client.deleteBucket(deleteBucketRequest);
System.out.println("Bucket ["+bucketName+"] is deleted.");
	}
	
	//DONE! uploads an InputStream to S3Bucket (as an object)
	//TO-DO SambaSender upload methode algorithme voorbeeld toepassen!
	public void uploadObject(String bucketName, String objectKey, ParameterValueList pvl) throws SenderException
	{		
		boolean bucketExistsThrowsException = false;
		bucketCreationForObjectAction(bucketName, bucketExistsThrowsException);
		if(!s3Client.doesObjectExist(bucketName, objectKey))
		{
			InputStream inputStream = (InputStream) pvl.getParameterValue("file").getValue();
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType("application/octet-stream");	
			PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectKey, inputStream, metadata);
			s3Client.putObject(putObjectRequest);
System.out.println("Object uploaded into a S3 bucket!: ["+bucketName+"]");
		}
		else
			throw new SenderException(getLogPrefix() + " object with given name already exists, please specify a new name for your object");			
	}
	
	//DONE! gets an object from S3 bucket
	public void downloadObject(String bucketName, String objectKey) throws SenderException
	{
		checkBucketAbsence(bucketName);
		checkObjectAbsence(bucketName, objectKey);
		try
		{
			GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
			S3Object s3Object = s3Client.getObject(getObjectRequest);
			S3ObjectInputStream s3InputStream = s3Object.getObjectContent(); //slaan de data van S3 object in s3InputStream op
			Misc.streamToFile(s3InputStream, new File("C:\\Users\\Robert\\Desktop\\ObjectContentFromS3Bucket.jpg"));
System.out.println("Object downloaded and all the streams closed!");
/*			FileOutputStream fileOutputStream = new FileOutputStream(new File("C:\\Users\\Robert\\Desktop\\ObjectContentFromS3Bucket.jpg"));
			byte[] readBuffer = new byte[s3InputStream.available()];
			int readLength = 0;
	        while ((readLength = s3InputStream.read(readBuffer)) != -1) 
	        {
	            fileOutputStream.write(readBuffer, 0, readLength);
	        }
	        //Misc.streamToString(s3InputStream)
*/	        
	        s3InputStream.close();
		}
		catch (FileNotFoundException e1)
		{
			e1.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	//DONE! copies object from one bucket to another (new)bucket
	public void copyObject(String bucketName, String objectKey, ParameterValueList pvl) throws SenderException
	{
		checkBucketAbsence(bucketName);
		checkObjectAbsence(bucketName, objectKey);
		String destinationBucketName = pvl.getParameterValue("destinationBucketName").getValue().toString();
		String destinationObjectKey = pvl.getParameterValue("destinationObjectKey").getValue().toString();				
		if(BucketNameUtils.isValidV2BucketName(destinationBucketName))
		{
			boolean bucketExistsThrowsException = false;
			bucketCreationForObjectAction(destinationBucketName, bucketExistsThrowsException);
			if(!s3Client.doesObjectExist(destinationBucketName, destinationObjectKey))
			{
				CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, objectKey, destinationBucketName, destinationObjectKey);
				s3Client.copyObject(copyObjectRequest);			
System.out.println("Object copied from bucket ["+bucketName+"] to bucket ["+destinationBucketName+"]");
			}
			else
				throw new SenderException(getLogPrefix() + " object with given name already exists, please specify a new name");
		}
		else
			throw new SenderException(getLogPrefix() + " failed to create bucket, correct bucket naming is not used for destinationBucketName parameter, visit AWS to see correct bucket naming");
	}
	
	//DONE! deletes an object from S3 bucket
	public void deleteObject(String bucketName, String objectKey) throws SenderException
	{
		checkBucketAbsence(bucketName);
		checkObjectAbsence(bucketName, objectKey);
		DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, objectKey);
		s3Client.deleteObject(deleteObjectRequest);
System.out.println("Object deleted from bucket: "+bucketName);
	}


	public void bucketCreationForObjectAction(String bucketName, boolean throwsException) throws SenderException
	{		
		if(isBucketCreationEnabled())
			createBucket(bucketName, throwsException);
		else
			throw new SenderException(getLogPrefix() + " failed to create a bucket, to create a bucket bucketCreationEnabled attribute must be assinged to 'true'");	
	}

	//when bucket 'does not exist' it throws an exception
	public void checkBucketAbsence(String bucketName) throws SenderException
	{
		if(!s3Client.doesBucketExistV2(bucketName))
			throw new SenderException(getLogPrefix() + " bucket with given name does not exist, please specify the name of an existing bucket");
	}
	
	//when object 'does not exist' it throws an exception
	public void checkObjectAbsence(String bucketName, String objectKey) throws SenderException
	{
		if(!s3Client.doesObjectExist(bucketName, objectKey))
			throw new SenderException(getLogPrefix() + " object with given name does not exist, please specify the name of an existing object");
	}

	
	//getters and setters
	public List<String> getAvailableRegions()
	{
		//this method checks for available regions in AWS
		List<String> availableRegions = new ArrayList<String>(Regions.values().length);
		for (Regions region : Regions.values())
		{
			availableRegions.add(region.getName());
			//System.out.println(region.getName());
		}
		
		return availableRegions;
	}
	
	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public boolean isChunkedEncodingDisabled()
	{
		return chunkedEncodingDisabled;
	}

	public void setChunkedEncodingDisabled(boolean chunkedEncodingDisabled)
	{
		this.chunkedEncodingDisabled = chunkedEncodingDisabled;
	}

	public boolean isAccelerateModeEnabled()
	{
		return accelerateModeEnabled;
	}

	public void setAccelerateModeEnabled(boolean accelerateModeEnabled)
	{
		this.accelerateModeEnabled = accelerateModeEnabled;
	}

	public boolean isForceGlobalBucketAccessEnabled()
	{
		return forceGlobalBucketAccessEnabled;
	}

	public void setForceGlobalBucketAccessEnabled(boolean forceGlobalBucketAccessEnabled)
	{
		this.forceGlobalBucketAccessEnabled = forceGlobalBucketAccessEnabled;
	}
	
	public boolean isBucketCreationEnabled()
	{
		return bucketCreationEnabled;
	}

	public void setBucketCreationEnabled(boolean bucketCreationEnabled)
	{
		this.bucketCreationEnabled = bucketCreationEnabled;
	}

	
	public String getClientRegion()
	{
		return clientRegion;
	}

	public void setClientRegion(String clientRegion)
	{
		this.clientRegion = clientRegion;
	}
	
	public String getBucketRegion()
	{
		return bucketRegion;
	}

	public void setBucketRegion(String bucketRegion)
	{
		this.bucketRegion = bucketRegion;
	}

	public String getActions()
	{
		return actions;
	}
	
	public void setActions(String actions)
	{
		this.actions = actions;
	}
	
	public String getBucketName()
	{
		return bucketName;
	}

	public void setBucketName(String bucketName)
	{
		this.bucketName = bucketName;
	}
}