/******************************************************************************
 * Copyright (C) 2010-2016 CERN. All rights not expressly granted are reserved.
 *
 * This file is part of the CERN Control and Monitoring Platform 'C2MON'.
 * C2MON is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the license.
 *
 * C2MON is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with C2MON. If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/
package cern.c2mon.daq.tools;

import cern.c2mon.daq.common.logger.EquipmentLogger;
import cern.c2mon.daq.common.logger.EquipmentLoggerFactory;
import cern.c2mon.shared.common.datatag.DataTagDeadband;
import cern.c2mon.shared.common.datatag.SourceDataQuality;
import cern.c2mon.shared.common.datatag.SourceDataTag;
import cern.c2mon.shared.common.datatag.SourceDataTagValue;
import cern.c2mon.shared.common.filter.FilteredDataTagValue.FilterType;
import cern.c2mon.shared.common.type.TypeConverter;

import java.util.Arrays;

import static java.lang.String.format;

/**
 * Class with all possible filters for Data Tag Values
 *
 * @author vilches
 */
public class DataTagValueFilter {
	/**
	 * The logger of this class
	 */
	protected EquipmentLogger equipmentLogger;

	 /**
     * Factor to calculate from a percentage value to a simple factor.
     */
    private static final double PERCENTAGE_FACTOR = 0.01;

	/**
	 * Creates a new Data Tag Value Filter which uses the provided equipment logger to log its results.
	 *
	 * @param equipmentLogger The equipment logger to use
	 */
	public DataTagValueFilter(final EquipmentLoggerFactory equipmentLoggerFactory) {
		this.equipmentLogger = equipmentLoggerFactory.getEquipmentLogger(getClass());;
	}

	/**
	 * This method is responsible for checking if the new value of the particular SourceDataTag should be sent to the
	 * application server or not. The decision is taken based on the deadband specification of the considered tag and
	 * assumes that the new update is valid (= quality OK).
	 *
	 * @param currentTag the current of the tag
	 * @param newTagValue new value of the SourceDataTag, received from a data source.
	 * @return True if the value is filtered else false.
	 */
	public boolean isValueDeadbandFiltered(final SourceDataTag currentTag, final Object newTagValue,
			final String newTagValueDesc) {
		return isValueDeadbandFiltered(currentTag, newTagValue, newTagValueDesc, new SourceDataQuality());
	}

	/**
	 * This method is responsible for checking if the new value of the particular SourceDataTag should be sent to the
	 * application server or not. The decision is taken based on the deadband specification of the considered tag
	 *
	 * @param currentTag the current of the tag
	 * @param newTagValue new value of the SourceDataTag, received from a data source.
	 * @param newTagValueDesc the new value description
	 * @param newSDQuality the new tag quality
	 * @return True if the value is filtered else false.
	 */
	private boolean isValueDeadbandFiltered(final SourceDataTag currentTag, final Object newTagValue,
			final String newTagValueDesc, final SourceDataQuality newSDQuality) {
		if (this.equipmentLogger.isTraceEnabled()) {
			this.equipmentLogger.trace(format("entering valueDeadbandFilterOut(%d)..", currentTag.getId()));
		}

		boolean filterTag = false;
		float valueDeadband;
		if (currentTag.getAddress().isProcessValueDeadbandEnabled()) {

			if (isCurrentValueAvailable(currentTag)
					&& (currentTag.getCurrentValue().getQuality().getQualityCode() == newSDQuality.getQualityCode())) {
				valueDeadband = currentTag.getAddress().getValueDeadband();


				if(TypeConverter.isNumber(currentTag.getDataType())){
					if (isCurrentValueAvailable(currentTag)) {
						Number currentValue = (Number) currentTag.getCurrentValue().getValue();
						Number newValue = newTagValue == null ? null : (Number) newTagValue;
						// Switch between absolute and relative value deadband
						switch (currentTag.getAddress().getValueDeadbandType()) {
						case DataTagDeadband.DEADBAND_PROCESS_ABSOLUTE:
							filterTag = isAbsoluteValueDeadband(currentValue, newValue, valueDeadband);
							break;
						case DataTagDeadband.DEADBAND_PROCESS_ABSOLUTE_VALUE_DESCR_CHANGE:

							String tagValueDesc = currentTag.getCurrentValue().getValueDescription();
							if (tagValueDesc == null) {
								tagValueDesc = "";
							}

							String newValueDesc = newTagValueDesc;
							if (newValueDesc == null) {
								newValueDesc = "";
							}
							String currentValueDesc = currentTag.getCurrentValue().getValueDescription();
							if (currentValueDesc == null) {
								currentValueDesc = "";
							}

							// check if the value description has changed, if yes - then do not apply deadband filtering
							if (tagValueDesc.equals(newValueDesc)) {
								filterTag = isAbsoluteValueDeadband(currentValue, newValue, valueDeadband);
							}
							break;

						case DataTagDeadband.DEADBAND_PROCESS_RELATIVE:
							filterTag = isRelativeValueDeadband(currentValue, newValue, valueDeadband);
							break;
						case DataTagDeadband.DEADBAND_PROCESS_RELATIVE_VALUE_DESCR_CHANGE:

							tagValueDesc = currentTag.getCurrentValue().getValueDescription();
							if (tagValueDesc == null) {
								tagValueDesc = "";
							}

							newValueDesc = newTagValueDesc;
							if (newValueDesc == null) {
								newValueDesc = "";
							}
							currentValueDesc = currentTag.getCurrentValue().getValueDescription();
							if (currentValueDesc == null) {
								currentValueDesc = "";
							}

							// check if the value description has changed, if yes - then do not apply deadband filtering
							if (tagValueDesc.equals(newValueDesc)) {
								filterTag = isRelativeValueDeadband(currentValue, newValue, valueDeadband);
							}
							break;

						default:
							// do nothing
							break;
						}
					}
				}

			}// if

		}// if

		if (this.equipmentLogger.isTraceEnabled()) {
			this.equipmentLogger.trace(format("leaving valueDeadbandFilterOut(%d); filter out = %b", currentTag.getId(), filterTag));
		}

		return filterTag;
	}

	/**
     * Compares the value, quality and time stamp information of the current {@link SourceDataTagValue} against the newly received
     * quality information.
     * Avoid sending twice (one by one) 2 invalid tags with the same quality code and description
     *
     * Currently used by EquipmentSenderInvalid
     *
     * @param currentTag The current tag object of the {@link SourceDataTag} that shall be updated
     * @param newValue The new update value that we want set to the tag
     * @param newTagValueDesc The new update value description
     * @param newSDQuality The new quality info for the {@link SourceDataTag} that shall be updated
     * @param newSourceTimestamp The new source timestamp
     *
     * @return <code>FilterType</code>, if this the new quality is a candidate for being filtered out it will return the
     * reason if not it will return <code>FilterType.NO_FILTERING</code>
     */
    public FilterType isCandidateForFiltering(final SourceDataTag currentTag, final Object newValue, final String newTagValueDesc,
        final SourceDataQuality newSDQuality, final long newSourceTimestamp) {
      this.equipmentLogger.debug("isCandidateForFiltering - entering isCandidateForFiltering() for tag #" + currentTag.getId());

      SourceDataTagValue currentSDValue = currentTag.getCurrentValue();

      if (currentSDValue != null) {
        // Check if the new update is older or equal than the current value
        if (isOlderUpdate(newSDQuality, currentSDValue.getQuality(), newSourceTimestamp, currentSDValue.getTimestamp().getTime())) {
          // Check if it is repeated value
          FilterType result = isRepeatedValue(currentTag, newValue, newTagValueDesc, newSDQuality);

          if ((result == FilterType.REPEATED_INVALID) || (result == FilterType.REPEATED_VALUE)) {
            // The value will be filtered out by result (REPEATED_INVALID, REPEATED_VALUE)
            return result;
          } else {
            // The value will be filtered out by OLD_UPDATE
            this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
                " - New timestamp is older than the current timestamp. Candidate for filtering");
            return FilterType.OLD_UPDATE;
          }
        }

        // Check if the value is
        return isRepeatedValue(currentTag, newValue, newTagValueDesc, newSDQuality);
      }
      // in case the SourceDataTag value has never been initialized we don't want to filter
      else {
        this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentTag.getId() +
            " - Current Source Data Tag Value null but we have a New value. Not candidate for filtering");
      }

      // We got a new quality information that we want to send to the server.
      return FilterType.NO_FILTERING;
    }

    /**
     * IN addition to isCandidateForFiltering it compares the value and quality information of the current
     * {@link SourceDataTagValue} against the newly received quality information.
     * Avoid sending twice (one by one) 2 invalid tags with the same quality code and description
     *
     * @param currentTag The current tag object of the {@link SourceDataTag} that shall be updated
     * @param newValue The new update value that we want set to the tag
     * @param newTagValueDesc The new update value description
     * @param newSDQuality The new quality info for the {@link SourceDataTag} that shall be updated
     * @param newSourceTimestamp The new source timestamp
     *
     * @return <code>FilterType</code>, if this the new quality is a candidate for being filtered out it will return the
     * reason if not it will return <code>FilterType.NO_FILTERING</code>
     */
    private FilterType isRepeatedValue(final SourceDataTag currentTag, final Object newValue, final String newTagValueDesc,
        final SourceDataQuality newSDQuality) {

      SourceDataTagValue currentSDValue = currentTag.getCurrentValue();
			FilterType filtering;


      if(newValue !=  null && newValue.getClass().isArray()){
				filtering = isDifferentArrayValue(currentTag, newValue);
      } else {
				filtering = isDifferentValue(currentTag, newValue, newTagValueDesc, newSDQuality);
      }

      if(filtering != null) {
        return filtering;
      }

      // The two values are both null or equal. Now we check for redundant Value Description information
			if ((filtering = isDifferentValueDescription(currentSDValue, newTagValueDesc)) != null){
				return filtering;
			}

      // Current and new Values and Value Descriptions are both null or equal! Now we check for redundant quality information
      if ((filtering = isDifferentDataTagQuality(currentSDValue, newSDQuality)) != null){
        return filtering;
      }

      // We got a new quality information that we want to send to the server.
      return FilterType.NO_FILTERING;
    }


	private FilterType isDifferentValue(final SourceDataTag currentTag, final Object newValue, final String newTagValueDesc,
                                      final SourceDataQuality newSDQuality){
		FilterType filtering = null;
		SourceDataTagValue currentSDValue = currentTag.getCurrentValue();

		if (currentSDValue.getValue() == null && newValue != null) {
			// Got a new value which is initializing our SourceDataTag. Hence we do not want to filter it out!
			this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
					" - Current Value null but we have a New value. Not candidate for filtering");

			return FilterType.NO_FILTERING;
		}
		else if (currentSDValue.getValue() != null && !currentSDValue.getValue().equals(newValue)) {
			// The two value are different, hence we do not want to filter it out ... unless the Value dead band filter said the opposite
			if (isValueDeadbandFiltered(currentTag, newValue, newTagValueDesc, newSDQuality)) {
				this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
						" - New value update but within value deadband filter. Candidate for filtering");

				return FilterType.VALUE_DEADBAND;
			}

			// The two values are different, so it is clear we do not want to filter it out!
			this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId()
					+ " - Both Values are different (Current vs New) = (" + currentSDValue.getValue() + " vs " + newValue
					+ "). Not candidate for filtering");

			return FilterType.NO_FILTERING;
		}

		return filtering;
	}


  /**
   * Helper method which compares the values of the new DataTag and the vale of the previous one.
   * The type of the value is complex which means it is either an array or an arbitrary object.
   *
   * @param currentTag
   * @param newValue
   * @param newTagValueDesc
   * @param newSDQuality
   * @return
   */
  private FilterType isDifferentArrayValue(final SourceDataTag currentTag, final Object newValue){
    FilterType filtering = null;
    SourceDataTagValue currentSDValue = currentTag.getCurrentValue();

    if (currentSDValue.getValue() == null && newValue != null) {
      // Got a new value which is initializing our SourceDataTag. Hence we do not want to filter it out!
      this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
          " - Current Value null but we have a New value. Not candidate for filtering");

      return FilterType.NO_FILTERING;
    }
    // check if the old value type and the new one are both array.
    else if (currentSDValue.getValue() != null
        && currentSDValue.getValue().getClass().isArray() && newValue.getClass().isArray()) {

      if(!Arrays.equals((Object[]) currentSDValue.getValue(), (Object[]) newValue)){

        this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId()
            + " - Both Values are different (Current vs New) = (" + currentSDValue.getValue() + " vs " + newValue
            + "). Not candidate for filtering");

        return FilterType.NO_FILTERING;
      }

      // both values are no array so there must be an arbitrary object.
    } else if(currentSDValue.getValue() != null) {

      if(!currentSDValue.getValue().equals(newValue)){

        this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId()
            + " - Both Values are different (Current vs New) = (" + currentSDValue.getValue() + " vs " + newValue
            + "). Not candidate for filtering");

        return FilterType.NO_FILTERING;
      }

    }

    return filtering;
  }

	private FilterType isDifferentValueDescription(SourceDataTagValue currentSDValue, final String newTagValueDesc ){
		FilterType filtering = null;

    // The two values are both null or equal. Now we check for redundant Value Description information
		if (!currentSDValue.getValueDescription().equalsIgnoreCase(newTagValueDesc)
				&& ((newTagValueDesc != null) || !currentSDValue.getValueDescription().isEmpty())) {
        /*
         * Note 1: currentSDValue.getValueDescription() will never be null
         * Note 2: if getValueDescription is empty and newTagValueDesc is null we get not equal but
         *         for us will be equal (no value) so we take care of this special case and continue the checks
         */

			// The two value Descriptions are different
			this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
					" - Both Values are equal but Value Descriptions are different. Not candidate for filtering");

			return FilterType.NO_FILTERING;
		}

		return filtering;
	}

  private FilterType isDifferentDataTagQuality(SourceDataTagValue currentSDValue, final SourceDataQuality newSDQuality){
    FilterType filtering = null;
    short newQualityCode = newSDQuality.getQualityCode();

    if (currentSDValue.getQuality() != null) {
      // Check, if quality code did not change
      if ((currentSDValue.getQuality().getQualityCode() == newQualityCode)) {
        // Only checks description is Quality is no OK (Invalids)
        if(newQualityCode != SourceDataQuality.OK) {
          this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
              " - Both Value, Value Description and Quality codes are equal. Check Quality Descriptions to take a decision");

          // Check if quality description did not change. If it is not null we compare it with the new one
          if (currentSDValue.getQuality().getDescription() == null) {
            // If description is null we cannot compare so we check directly if both are null or not
            if (newSDQuality.getDescription() == null) {
              // We filter out since both are the same and null
              this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
                  " - Both Quality Descriptions are null. Candidate for filtering");

              return FilterType.REPEATED_INVALID;
            }
            else {
              this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
                  " - Current Quality Description null but we have a New Quality Description. Not candidate for filtering");

              // Goes directly to the final return
            }
          }
          // Description is not null. We can compare it with the new description
          else if (currentSDValue.getQuality().getDescription().equals(newSDQuality.getDescription())) {
            // If we are here, it means we have received a redundant quality code and description ==> should be filtered out.
            this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
                " - Both Value, Value Description, Quality and Quality Descriptions are equal. Candidate for filtering");

            return FilterType.REPEATED_INVALID;
          } else {
            this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
                " - Current Quality Description and New Quality Description are different. Not candidate for filtering");

            // Goes directly to the final return
          }
        } else {
          this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
              " - Both Value, Value Description and Quality codes (OK) are equal");

          return FilterType.REPEATED_VALUE;
        }
      }
      // Different Quality Codes
      else {
        this.equipmentLogger.trace("isCandidateForFiltering - Tag " + currentSDValue.getId() +
            " - Both Value and Value Description are equal but Quality Codes are different. Not candidate for filtering");
      }
    }
    return filtering;
  }

  /**
     * Returns true if the difference of the provided numbers is smaller than the value deadband.
     *
     * @param currentValue The current value of the tag.
     * @param newValue The new value of the tag.
     * @param valueDeadband The value deadband.
     * @return True if the absolute value deadband fits else false.
     */
    public boolean isAbsoluteValueDeadband(final Number currentValue, final Number newValue, final float valueDeadband) {
        this.equipmentLogger.trace("entering isAbsoluteValueDeadband()..");
        boolean isAbsoluteValueDeadband = currentValue != null && newValue != null
                && Math.abs(currentValue.doubleValue() - newValue.doubleValue()) < valueDeadband;
        this.equipmentLogger.trace("leaving isAbsoluteValueDeadband().. Result: " + isAbsoluteValueDeadband);
        return isAbsoluteValueDeadband;
    }

    /**
     * Returns true if difference of the values is higher than the current value multiplied with the time deadband
     * (divided by 100).
     *
     * @param currentValue The current value of the tag.
     * @param newValue The new value of the tag.
     * @param valueDeadband The value deadband in %.
     * @return True if the relative value deadband fits else false.
     */
    public boolean isRelativeValueDeadband(final Number currentValue, final Number newValue, final float valueDeadband) {
        this.equipmentLogger.trace("entering isRelativeValueDeadband()..");
        boolean isRelativeValueDeadband = false;
        if (currentValue == null || newValue == null) {
            isRelativeValueDeadband = false;
        } else if (currentValue.equals(newValue)) {
            isRelativeValueDeadband = true;
        } else {
            double curDoubleValue = currentValue.doubleValue();
            if (curDoubleValue != 0) {
                // valueDeadband divided by 100 to go from % to a factor
                double maxDiff = curDoubleValue * valueDeadband * PERCENTAGE_FACTOR;
                double realDiff = Math.abs(curDoubleValue - newValue.doubleValue());
                isRelativeValueDeadband = realDiff < maxDiff;
            }
        }
        this.equipmentLogger.trace("leaving isRelativeValueDeadband().. Result: " + isRelativeValueDeadband);
        return isRelativeValueDeadband;
    }

	/**
	 * Checks if there is a not null value for this tag.
	 *
	 * @param tag The tag to check.
	 * @return Returns true if a not null value is available else false.
	 */
	private boolean isCurrentValueAvailable(final SourceDataTag tag) {
		boolean isAvailable = (tag.getCurrentValue() != null) && (tag.getCurrentValue().getValue() != null);

		if (this.equipmentLogger.isTraceEnabled())
			this.equipmentLogger.trace(format("isCurrentValueAvailable - Tag %d : %b", tag.getId(), isAvailable));

		return isAvailable;
	}

	/**
	 * Checks if the new Timestamp is older than the current one and if so it checks the Quality code
	 * to decide if the value has to be filtered out or not.
	 *
	 * Filter when:
	 * - New TS < Current TS + Current has not DATA_UNAVAILABLE Quality
	 * - New TS < Current TS + Current has DATA_UNAVAILABLE Quality + New Bad Quality
	 *
	 * No filter when:
	 * - New TS < Current TS + Current has DATA_UNAVAILABLE Quality + New Good Quality
	 * - New TS >= Current TS
	 *
	 * @param newSDQuality new Source Data Tag Quality
	 * @param currentSDQuality current Source Data Tag Quality
	 * @param newTimestamp new source Timestamp
	 * @param currentTimestamp current source Timestamp
	 * @return True if the New value has to be filter out. False if any other case.
	 */
	protected boolean isOlderUpdate(final SourceDataQuality newSDQuality, final SourceDataQuality currentSDQuality,
	    final long newTimestamp, final long currentTimestamp) {
	  this.equipmentLogger.debug("isOlderUpdate - entering isOlderUpdate()");

	  // if New TS is older to the current TS we may have a filtering use case
	  if (newTimestamp < currentTimestamp) {
	    this.equipmentLogger.trace("isOlderUpdate - New timestamp is older or equal than current TS (" + newTimestamp + ", " + currentTimestamp +")");
      // New timestamp is older or equal than current TS. Check the Quality
      if (currentSDQuality.getQualityCode() == SourceDataQuality.DATA_UNAVAILABLE) {
        // Exceptional case for not applying this filter:
        // If current tag was unavailable we allow sending tag value with good quality but old source time stamp
        if (newSDQuality.isValid()) {
          // New value has Good Quality. Swapping to valid to invalid case. No filter
          this.equipmentLogger.trace("isOlderUpdate - The current value has DATA_UNAVAILABLE Quality but new value has Good Quality. Not filter");
          return false;
        } else {
          // New value has Bad Quality. Filter
          this.equipmentLogger.trace("isOlderUpdate - The current value has DATA_UNAVAILABLE Quality and new value has Bad Quality. Filter out ");
          return true;
        }
      } else {
        // The current value has any Quality but DATA_UNAVAILABLE. Filter
        this.equipmentLogger.trace("isOlderUpdate - The current value quality is different to DATA_UNAVAILABLE. Filter out ");
        return true;
      }
	  }

	  // New TS is newer than current TS
	  this.equipmentLogger.trace("isOlderUpdate - New timestamp is newer or equal than current TS. Not filter");
	  return false;
	}
}