import React, { FC, useState } from 'react';
import * as yup from 'yup';
import { useHistory } from 'react-router-dom';
import API from '../config/baseURL';
import { AxiosResponse } from 'axios';
import ZadatKod from './Components/ZadatKod';
import ZobrazitStav from './Components/ZobrazitStav';

interface ITrackovaniNalezu {}

export enum STEPS {
    ZadatKod,
    ZobraitStav,
}

export type syringeStateType = 'destroyed' | 'reserved' | 'announced' | 'notfound';

export interface ISyringeState {
    hasCheckMark: boolean;
    firstLine: string;
    secondLine: string;
}

type syringeStateTypes = {
    [key in syringeStateType]?: ISyringeState;
};

const syringeStates: syringeStateTypes = {
    destroyed: {
        hasCheckMark: true,
        firstLine: 'nález byl úspěšně',
        secondLine: 'ZLIKVIDOVÁN',
    },
    reserved: {
        hasCheckMark: false,
        firstLine: 'pracujeme na tom, nález je',
        secondLine: 'REZEROVAVNÝ k likvidaci',
    },
    announced: {
        hasCheckMark: true,
        firstLine: 'nález je',
        secondLine: 'NAHLÁŠENÝ na městskou policii',
    },
    notfound: {
        hasCheckMark: false,
        firstLine: 'jehla',
        secondLine: 'NEBYLA nalezena',
    },
};

const TrackovaniNalezu: FC<ITrackovaniNalezu> = ({}) => {
    const [currentStep, setCurrentStep] = useState<STEPS>(STEPS.ZadatKod);
    const [syringeState, setSyringeState] = useState<syringeStateType>('announced');
    const history = useHistory();

    const handleOnClickBack = (event: React.MouseEvent<HTMLButtonElement>) => {
        history.goBack();
    };

    const handleStepChange = (newStep: STEPS) => {
        setCurrentStep(newStep);
    };

    const handleNewSyringeState = (syringeState: syringeStateType) => {
        setSyringeState(syringeState);
    };

    switch (currentStep) {
        case STEPS.ZobraitStav:
            return <ZobrazitStav syringeState={syringeStates[syringeState]!} />;
        default:
            return <ZadatKod onClickBack={handleOnClickBack} handleStepChange={handleStepChange} handleNewSyringeState={handleNewSyringeState} />;
    }
};

export default TrackovaniNalezu;
