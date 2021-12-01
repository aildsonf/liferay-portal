import { useQuery } from '@apollo/client';
import { useContext, useEffect, useState } from 'react';
import {
	onboardingPageGuard,
	overviewPageGuard,
	usePageGuard,
} from '../../../../common/hooks/usePageGuard';
import {
	getAccountSubscriptionGroupsByFilter,
	getKoroneikiAccounts,
} from '../../../../common/services/liferay/graphql/queries';
import SubscriptionsBar from '../../components/SubscriptionsBar';
import SubscriptionsCards from '../../components/SubscriptionsCards';
import { AppContext } from '../../context';
import { actionTypes } from '../../context/reducer';
import { CUSTOM_EVENTS } from '../../utils/constants';

const Overview = ({ userAccount }) => {
	const [{ project }, dispatch] = useContext(AppContext);
  const [subscriptionsTags, setSubscriptionsTags] = useState(() => []);
  const [selectedTag, setSelectedTag] = useState(() => '')

	const { isLoading } = usePageGuard(
		userAccount,
		overviewPageGuard,
		onboardingPageGuard,
		project.accountKey
	);
  
	const { data, isLoading: isLoadingKoroneiki } = useQuery(
		getKoroneikiAccounts,
		{
			variables: {
				filter: `accountKey eq '${project.accountKey}'`,
			},
		}
	);

	const {
		data: accountSubscriptionsData,
		loading: isAccountSubscriptionsLoading,
	} = useQuery(getAccountSubscriptionGroupsByFilter, {
		variables: {
			filter: `accountKey eq '${project.accountKey}'`,
		},
	});

	useEffect(() => {
		if (!isLoading && data) {
			const koroneikiAccount = data.c?.koroneikiAccounts?.items[0];

			dispatch({
				payload: koroneikiAccount,
				type: actionTypes.UPDATE_PROJECT,
			});

			window.dispatchEvent(
				new CustomEvent(CUSTOM_EVENTS.PROJECT, {
					bubbles: true,
					composed: true,
					detail: koroneikiAccount,
				})
			);
		}
	}, [data, dispatch, isLoading]);

  useEffect(() => {
    if (!isAccountSubscriptionsLoading && accountSubscriptionsData) {
			setSubscriptionsTags(
				accountSubscriptionsData?.c?.accountSubscriptionGroups?.items ||
					[]
			);
		}
  }, [accountSubscriptionsData, isAccountSubscriptionsLoading])

	if (isLoading || isLoadingKoroneiki) {
		return <div>Overview Skeleton</div>;
	}

	return (
		<div>
			Overview Page
			{!isAccountSubscriptionsLoading && <SubscriptionsBar selectedTag={selectedTag} setSelectedTag={setSelectedTag} subscriptionsTags={subscriptionsTags} />}

      <SubscriptionsCards accountKey={project.accountKey} selectedTag={selectedTag}/>
		</div>
	);
};

export default Overview;
